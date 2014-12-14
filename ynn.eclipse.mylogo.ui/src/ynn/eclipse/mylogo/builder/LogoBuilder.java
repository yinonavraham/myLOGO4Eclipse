package ynn.eclipse.mylogo.builder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import ynn.eclipse.mylogo.markers.MarkersHelper;
import ynn.eclipse.mylogo.ui.util.Util;
import ynn.mylogo.model.ActionsRegistry;
import ynn.mylogo.model.runtime.Action;
import ynn.mylogo.parser.LogoParser;
import ynn.mylogo.parser.LogoParser.ParserResult;
import ynn.mylogo.ui.swt.RuntimeActionsExecutor;
import ynn.mylogo.ui.swt.TurtleCanvas;

public class LogoBuilder extends IncrementalProjectBuilder {

	public static final String BUILDER_ID = "ynn.eclipse.mylogo.ui.logoBuilder";

	class LogoDeltaVisitor implements IResourceDeltaVisitor {
		
		private final IProgressMonitor monitor;
		
		public LogoDeltaVisitor(IProgressMonitor monitor) {
			this.monitor = monitor;
		}
		
		@Override
		public boolean visit(IResourceDelta delta) throws CoreException {
			IResource resource = delta.getResource();
			if (resource.isDerived()) return false;
			switch (delta.getKind()) {
				case IResourceDelta.ADDED:
					// handle added resource
					buildResource(resource, this.monitor);
					break;
				case IResourceDelta.REMOVED:
					// handle removed resource
					removeResourceBuildArtifacts(resource, monitor);
					break;
				case IResourceDelta.CHANGED:
					// handle changed resource
					buildResource(resource, this.monitor);
					break;
			}
			//return true to continue visiting children.
			return true;
		}
	}

	class LogoResourceVisitor implements IResourceVisitor {
		
		private final IProgressMonitor monitor;
		
		public LogoResourceVisitor(IProgressMonitor monitor) {
			this.monitor = monitor;
		}
		
		@Override
		public boolean visit(IResource resource) {
			if (resource.isDerived()) return false;
			buildResource(resource, this.monitor);
			//return true to continue visiting children.
			return true;
		}
	}

	@Override
	protected IProject[] build(int kind, @SuppressWarnings("rawtypes") Map args, IProgressMonitor monitor) throws CoreException {
		if (kind == FULL_BUILD) {
			fullBuild(monitor);
		} else {
			IResourceDelta delta = getDelta(getProject());
			if (delta == null) {
				fullBuild(monitor);
			} else {
				incrementalBuild(delta, monitor);
			}
		}
		return null;
	}

	@Override
	protected void clean(final IProgressMonitor monitor) throws CoreException {
		// delete markers set and files created
		MarkersHelper.deleteMarkers(getProject());
		IFolder folder = getProject().getFolder("build");
		boolean force = true;
		folder.delete(force, monitor);
	}

	void removeResourceBuildArtifacts(IResource resource, IProgressMonitor monitor) throws CoreException {
		IFile file = (IFile) resource.getAdapter(IFile.class);
		if (file != null && "logo".equalsIgnoreCase(file.getFileExtension())) {
			IFile targetBuildFile = getTargetBuildFile(getProject(), file);
			if (targetBuildFile.exists()) {
				boolean force = false;
				targetBuildFile.delete(force, monitor);
			}
		}
	}
	
	void buildResource(IResource resource, IProgressMonitor monitor) {
		if (resource instanceof IFile && resource.getName().endsWith(".logo")) {
			IFile file = (IFile) resource;
			ActionsRegistry registry = new ActionsRegistry();
			LogoParser parser = new LogoParser(registry);
			try {
				String text = Util.readFileContent(file);
				ParserResult result = parser.parse(text);
				updateMarkers(file, result);
				if (result.getErrors().isEmpty()) {
					createImage(file, result, monitor);
				}
			} catch (Exception e) {
				//TODO Handle exception
				e.printStackTrace();
			}
		}
	}

	private void createImage(IFile file, final ParserResult result, IProgressMonitor monitor) throws CoreException {
		IProject project = file.getProject();
		final IFile targetFile = getTargetBuildFile(project, file);
		final boolean force = true;
		final boolean keepHistory = false;
		final ByteArrayOutputStream output = new ByteArrayOutputStream();
		Display display = Display.getDefault();
		display.syncExec(new Runnable() {
			@Override
			public void run() {
				Shell shell = new Shell();
				TurtleCanvas canvas = new TurtleCanvas(shell, SWT.NONE);
				List<Action> actions = result.getActions();
				RuntimeActionsExecutor executor = new RuntimeActionsExecutor(canvas);
				for (Action action : actions)
				{
					action.accept(executor);
				}
				
				Rectangle bounds = canvas.calcBounds();
				canvas.setSize(bounds.width+1, bounds.height+1);
				canvas.setOffset(new Point(-bounds.x, -bounds.y));
				canvas.setBackground(shell.getDisplay().getSystemColor(SWT.COLOR_WHITE));
				
				Image drawable = new Image(shell.getDisplay(), canvas.getBounds());
				GC gc = new GC(drawable);
				canvas.print(gc);
				ImageLoader loader = new ImageLoader();
				loader.data = new ImageData[] {drawable.getImageData()};
				loader.save(output, SWT.IMAGE_PNG);
				drawable.dispose();
				gc.dispose();
			}
		});
		if (output.size() > 0) {
			InputStream inputStream = new ByteArrayInputStream(output.toByteArray());
			if (targetFile.exists()) {
				targetFile.setContents(inputStream, force, keepHistory, monitor);
			} else {
				IPath relativePath = targetFile.getProjectRelativePath().removeLastSegments(1);
				createPath(project, relativePath, monitor);
				targetFile.create(inputStream, force, monitor);	
			}	
		}
	}

	private IFile getTargetBuildFile(IProject project, IFile file) {
		IPath path = file.getProjectRelativePath().removeFileExtension();
		path = path.addFileExtension("png");
		IPath buildRootPath = project.getProjectRelativePath().append("build");
		path = buildRootPath.append(path);
		final IFile targetFile = project.getFile(path);
		return targetFile;
	}

	private void createPath(IProject project, IPath projectRelativePath, IProgressMonitor monitor) throws CoreException {
		if (projectRelativePath.isEmpty()) return; // Return if this is the project's root
		IFolder folder = project.getFolder(projectRelativePath);
		if (folder.exists()) return;
		else {
			IPath folderRelativePath = folder.getProjectRelativePath(); 
			createPath(project, folderRelativePath.removeLastSegments(1), monitor);
			boolean force = true;
			boolean local = true;
			folder.create(force , local , monitor);
			folder.setDerived(true, monitor);
		}
	}

	void updateMarkers(IFile file, ParserResult result) { 
		MarkersHelper.deleteMarkers(file);
		MarkersHelper.addMarkers(file, result.getErrors());
	}

	protected void fullBuild(final IProgressMonitor monitor) throws CoreException {
		try {
			getProject().accept(new LogoResourceVisitor(monitor));
		} catch (CoreException e) {
			//TODO Handle exception
		}
	}

	protected void incrementalBuild(IResourceDelta delta, IProgressMonitor monitor) throws CoreException {
		// the visitor does the work.
		delta.accept(new LogoDeltaVisitor(monitor));
	}
}

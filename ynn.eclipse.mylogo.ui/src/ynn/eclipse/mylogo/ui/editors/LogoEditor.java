package ynn.eclipse.mylogo.ui.editors;


import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.part.MultiPageEditorPart;

import ynn.eclipse.mylogo.markers.MarkersHelper;
import ynn.mylogo.model.ActionsRegistry;
import ynn.mylogo.model.runtime.Action;
import ynn.mylogo.parser.LogoParser;
import ynn.mylogo.parser.LogoParser.ErrorEntry;
import ynn.mylogo.parser.LogoParser.ParserResult;
import ynn.mylogo.ui.swt.RuntimeActionsExecutor;
import ynn.mylogo.ui.swt.TurtleCanvas;

/**
 * A LOGO Script Editor
 */
public class LogoEditor extends MultiPageEditorPart implements IResourceChangeListener
{

	/** The text _scriptEditor used in page 0. */
	private LogoScriptEditor _scriptEditor;
	
	private TurtleCanvas _canvas;
	
	private Text _fldCommand;
	private Button _btnGo;
	private ActionsRegistry _actionsRegistry;

	/**
	 * Creates a LOGO script _scriptEditor example.
	 */
	public LogoEditor()
	{
		super();
		ResourcesPlugin.getWorkspace().addResourceChangeListener(this);
		_actionsRegistry = new ActionsRegistry();
	}

	/**
	 * Creates page 0 of the multi-page LOGO Script _scriptEditor, which contains a text _scriptEditor.
	 */
	void createPage0()
	{
		try
		{
			_scriptEditor = new LogoScriptEditor();
			int index = addPage(_scriptEditor, getEditorInput());
			setPageText(index, _scriptEditor.getTitle());
		}
		catch (PartInitException e)
		{
			ErrorDialog.openError(getSite().getShell(), "Error creating nested text _scriptEditor", null, e.getStatus());
		}
	}

	/**
	 * Creates page 1 of the multi-page LOGO Script _scriptEditor, which gives a visual display of the script
	 */
	void createPage1()
	{

		Composite composite = new Composite(getContainer(), SWT.NONE);
		GridLayout layout = new GridLayout();
		composite.setLayout(layout);
		layout.numColumns = 2;
		GridData data;
		
		_canvas = new TurtleCanvas(composite, SWT.NONE);
		data = new GridData(GridData.FILL_BOTH);
		data.horizontalSpan = 2;
		_canvas.setLayoutData(data);
		
		_fldCommand = new Text(composite, SWT.BORDER);
		data = new GridData(GridData.FILL_HORIZONTAL);
		_fldCommand.setLayoutData(data);
		_fldCommand.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyReleased(KeyEvent e)
			{
				if (e.keyCode == SWT.CR)
				{
					doCommand();
					_fldCommand.setText("");
				}
			}
		});
		_btnGo = new Button(composite, SWT.PUSH);
		_btnGo.setText("Go");
		_btnGo.addSelectionListener(new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent arg0)
			{
				doCommand();
				_fldCommand.setText("");
			}
		});
		

		int index = addPage(composite);
		setPageText(index, "Turtle Canvas");
	}

	/**
	 * Creates the pages of the multi-page LOGO Script _scriptEditor.
	 */
	protected void createPages()
	{
		createPage0();
		createPage1();
	}

	/**
	 * The <code>MultiPageEditorPart</code> implementation of this
	 * <code>IWorkbenchPart</code> method disposes all nested editors.
	 * Subclasses may extend.
	 */
	public void dispose()
	{
		ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);
		super.dispose();
	}

	/**
	 * Saves the multi-page LOGO Script _scriptEditor's document.
	 */
	public void doSave(IProgressMonitor monitor)
	{
		getEditor(0).doSave(monitor);
	}

	/**
	 * Saves the multi-page LOGO Script _scriptEditor's document as another file. Also updates the
	 * text for page 0's tab, and updates this multi-page LOGO Script _scriptEditor's input to
	 * correspond to the nested _scriptEditor's.
	 */
	public void doSaveAs()
	{
		IEditorPart editor = getEditor(0);
		editor.doSaveAs();
		setPageText(0, editor.getTitle());
		setInput(editor.getEditorInput());
	}

	/*
	 * (non-Javadoc) Method declared on IEditorPart
	 */
	public void gotoMarker(IMarker marker)
	{
		setActivePage(0);
		IDE.gotoMarker(getEditor(0), marker);
	}

	/**
	 * The LOGO Script _scriptEditor's implementation of this method
	 * checks that the input is an instance of <code>IFileEditorInput</code>.
	 */
	public void init(IEditorSite site, IEditorInput editorInput) throws PartInitException
	{
		if (!(editorInput instanceof IFileEditorInput))
			throw new PartInitException("Invalid Input: Must be IFileEditorInput");
		super.init(site, editorInput);
		super.setPartName(editorInput.getName());
	}

	/*
	 * (non-Javadoc) Method declared on IEditorPart.
	 */
	public boolean isSaveAsAllowed()
	{
		return true;
	}

	/**
	 * Updates the _canvas when page 1 is activated
	 */
	protected void pageChange(int newPageIndex)
	{
		super.pageChange(newPageIndex);
		if (newPageIndex == 1)
		{
			updateTurtleCanvas();
		}
	}

	private void updateTurtleCanvas()
	{
		_actionsRegistry = new ActionsRegistry();
		_canvas.clear();
		String commands = _scriptEditor.getDocumentProvider().getDocument(_scriptEditor.getEditorInput()).get();
		ParserResult parserResult = execCommands(commands);
		List<ErrorEntry> errors = parserResult.getErrors();
		if (!errors.isEmpty()) {
			IFile file = ((IFileEditorInput)getEditorInput()).getFile();
			MarkersHelper.deleteMarkers(file);
			MarkersHelper.addMarkers(file, errors);
		}
	}

	protected void doCommand()
	{
		String commands = _fldCommand.getText();
		execCommands(commands);
	}
	
	private ParserResult execCommands(String commands)
	{
		LogoParser parser = new LogoParser(_actionsRegistry);
		ParserResult result = parser.parse(commands);
		List<Action> actions = result.getActions();
		RuntimeActionsExecutor executor = new RuntimeActionsExecutor(_canvas);
		for (Action action : actions)
		{
			action.accept(executor);
		}
		return result;
	}

	/**
	 * Closes all project files on project close.
	 */
	public void resourceChanged(final IResourceChangeEvent event)
	{
		if (event.getType() == IResourceChangeEvent.PRE_CLOSE)
		{
			Display.getDefault().asyncExec(new Runnable() {
				public void run()
				{
					IWorkbenchPage[] pages = getSite().getWorkbenchWindow().getPages();
					for (int i = 0; i < pages.length; i++)
					{
						if (((FileEditorInput) _scriptEditor.getEditorInput()).getFile().getProject()
								.equals(event.getResource()))
						{
							IEditorPart editorPart = pages[i].findEditor(_scriptEditor.getEditorInput());
							pages[i].closeEditor(editorPart, true);
						}
					}
				}
			});
		}
	}
}

package ynn.eclipse.mylogo.ui.editors;

import java.util.ResourceBundle;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.IVerticalRuler;
import org.eclipse.jface.text.source.projection.ProjectionSupport;
import org.eclipse.jface.text.source.projection.ProjectionViewer;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.texteditor.AbstractDecoratedTextEditor;
import org.eclipse.ui.texteditor.ContentAssistAction;
import org.eclipse.ui.texteditor.ITextEditorActionDefinitionIds;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

import ynn.eclipse.mylogo.ui.views.LogoScriptOutlinePage;
import ynn.mylogo.model.ActionsRegistry;
import ynn.mylogo.parser.LogoParser;
import ynn.mylogo.parser.LogoParser.ParserResult;
import ynn.mylogo.parser.Token;
import ynn.mylogo.parser.ast.AbstractNode;

public class LogoScriptEditor extends AbstractDecoratedTextEditor {

	private LogoScriptOutlinePage outlinePage;

	public LogoScriptEditor() {
	}
	
	@Override
	protected void initializeEditor() {
		super.initializeEditor();
		setSourceViewerConfiguration(new LogoScriptSourceViewerConfiguration(this));
	}
	
	@Override
	protected ISourceViewer createSourceViewer(Composite parent, IVerticalRuler ruler, int styles) {
		ISourceViewer viewer = new ProjectionViewer(parent, ruler, getOverviewRuler(), isOverviewRulerVisible(), styles);
		// ensure decoration support has been created and configured.
		getSourceViewerDecorationSupport(viewer);
		return viewer;
	}
	
	@Override
	public void createPartControl(Composite parent) {
		super.createPartControl(parent);
		
		final ProjectionViewer viewer = (ProjectionViewer) super.getSourceViewer();
		ProjectionSupport projectionSupport = new ProjectionSupport(viewer, getAnnotationAccess(), getSharedColors());
		projectionSupport.install();
		
		viewer.doOperation(ProjectionViewer.TOGGLE);
	}
	
	@Override
	protected void createActions() {
		super.createActions();
		ResourceBundle resourceBundle = ResourceBundle.getBundle("ynn.eclipse.mylogo.ui.res.contentAssist"); 
		Action action = new ContentAssistAction(resourceBundle, "ContentAssistProposal.", this); 
		String id = ITextEditorActionDefinitionIds.CONTENT_ASSIST_PROPOSALS;
		action.setActionDefinitionId(id);
		setAction("ContentAssistProposal", action); 
		markAsStateDependentAction("ContentAssistProposal", true);
	}
	
	@Override
	public Object getAdapter(@SuppressWarnings("rawtypes") Class adapter) {
		if (IContentOutlinePage.class == adapter) {
			if (outlinePage == null) {
				outlinePage = new LogoScriptOutlinePage();
				outlinePage.addSelectionChangedListener(new ISelectionChangedListener() {
					@Override
					public void selectionChanged(SelectionChangedEvent event) {
						ISelection selection = event.getSelection();
						if (selection instanceof IStructuredSelection) {
							Object element = ((IStructuredSelection) selection).getFirstElement();
							if (element instanceof AbstractNode) {
								Token token = ((AbstractNode) element).getToken();
								selectAndReveal(token.getStart(), token.getValue().length());
							}
						}
					}
				});
				final IDocument document = getSourceViewer().getDocument();
				document.addDocumentListener(new IDocumentListener() {
					@Override
					public void documentChanged(DocumentEvent event) {
						updateOutlinePage(document);
					}
					
					@Override
					public void documentAboutToBeChanged(DocumentEvent event) {}
				});
				updateOutlinePage(document);
			}
			return outlinePage;
		}
		return super.getAdapter(adapter);
	}

	private void updateOutlinePage(IDocument document) {
		String text = document.get();
		LogoParser parser = new LogoParser(new ActionsRegistry());
		ParserResult result = parser.parse(text);
		outlinePage.updateScriptModel(result.getScript());
	}

}

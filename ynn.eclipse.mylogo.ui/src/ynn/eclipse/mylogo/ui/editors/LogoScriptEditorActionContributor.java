package ynn.eclipse.mylogo.ui.editors;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ide.IDEActionFactory;
import org.eclipse.ui.texteditor.BasicTextEditorActionContributor;
import org.eclipse.ui.texteditor.ITextEditor;

public class LogoScriptEditorActionContributor extends BasicTextEditorActionContributor {

	public LogoScriptEditorActionContributor() {
	}

	public void setActiveEditor(IEditorPart part) {
		super.setActiveEditor(part);

		if (!(part instanceof ITextEditor))
			return;
		ITextEditor editor = (ITextEditor) part;

		IActionBars actionBars = getActionBars();
		if (actionBars == null)
			return;

		actionBars.setGlobalActionHandler(IDEActionFactory.ADD_TASK.getId(),

		getAction(editor, IDEActionFactory.ADD_TASK.getId()));
		actionBars.setGlobalActionHandler(IDEActionFactory.BOOKMARK.getId(),

		getAction(editor, IDEActionFactory.BOOKMARK.getId()));
	}

//	public void contributeToMenu(IMenuManager menu) {
//		IMenuManager m = menu.findMenuUsingPath(IWorkbenchActionConstants.M_EDIT);
//		m.appendToGroup(IWorkbenchActionConstants.FIND_EXT, getFindAction());
//	}

}

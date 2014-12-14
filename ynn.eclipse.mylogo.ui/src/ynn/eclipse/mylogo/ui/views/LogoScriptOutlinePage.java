package ynn.eclipse.mylogo.ui.views;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.views.contentoutline.ContentOutlinePage;

import ynn.eclipse.mylogo.ui.util.ASTVisitorAdapter;
import ynn.mylogo.parser.ast.ASTVisitable;
import ynn.mylogo.parser.ast.ASTVisitor;
import ynn.mylogo.parser.ast.ActionCallStatement;
import ynn.mylogo.parser.ast.ActionDefinitionStatement;
import ynn.mylogo.parser.ast.ErrorStatement;
import ynn.mylogo.parser.ast.IntegerValueExpression;
import ynn.mylogo.parser.ast.ParameterDefinitionExpression;
import ynn.mylogo.parser.ast.ParameterValueExpression;
import ynn.mylogo.parser.ast.Script;
import ynn.mylogo.parser.ast.StatementListValueExpression;

public class LogoScriptOutlinePage extends ContentOutlinePage {
	
	private Script script = null;

	@Override
	public void createControl(Composite parent) {
		super.createControl(parent);
		TreeViewer viewer = getTreeViewer();
		viewer.setContentProvider(new LogoScriptOutlineContentProvider());
		viewer.setLabelProvider(new LogoScriptOutlineLabelProvider());
		if (script != null) viewer.setInput(script);
	}

	public void updateScriptModel(final Script script) {
		this.script = script;
		final TreeViewer treeViewer = getTreeViewer();
		if (treeViewer == null) return;
		Display display = treeViewer.getTree().getShell().getDisplay();
		display.asyncExec(new Runnable() {
			@Override
			public void run() {
				treeViewer.setInput(script);
			}
		});
	}
	
	/* ****************
	 * Helper classes *
	 ******************/
	
	private class LogoScriptOutlineLabelProvider extends LabelProvider {
		
		private final ASTLabelVisitor labelProvider = new ASTLabelVisitor();
		
		@Override
		public String getText(Object element) {
			if (element instanceof ASTVisitable) {
				return labelProvider.getLabel((ASTVisitable) element);
			}
			return super.getText(element);
		}
		
	}
	
	private class ASTLabelVisitor extends ASTVisitorAdapter {
		
		private StringBuilder label = null;
		
		public String getLabel(ASTVisitable visitable) {
			label = new StringBuilder();
			visitable.accept(this);
			return label.toString();
		}
		
		@Override
		public void visit(ParameterValueExpression expression) {
			label.append(" ").append(expression.getName());
			super.visit(expression);
		}
		
		@Override
		public void visit(StatementListValueExpression expression) {
			label.append(" ").append(expression.toString());
//			super.visit(expression);
		}
		
		@Override
		public void visit(IntegerValueExpression expression) {
			label.append(" ").append(expression.getValue());
//			super.visit(expression);
		}
		
		@Override
		public void visit(ActionCallStatement statement) {
			label.append(statement.getName());
			super.visit(statement);
		}
		
		@Override
		public void visit(ErrorStatement statement) {
			label.append("Error: ").append(statement.getMessage());
//			super.visit(statement);
		}
		
		@Override
		public void visit(ParameterDefinitionExpression expression) {
			label.append(" :").append(expression.getName());
//			super.visit(expression);
		}
		
		@Override
		public void visit(ActionDefinitionStatement statement) {
			label.append("Definition: ").append(statement.getName());
//			super.visit(statement);
			for (ParameterDefinitionExpression expression : statement.getParameters()) {
				expression.accept(this);
			}
		}
		
	}
	
	private class LogoScriptOutlineContentProvider implements ITreeContentProvider {
		
		private ASTContentVisitor childrenProvider = new ASTContentVisitor();

		@Override
		public void dispose() {}

		@Override
		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {}

		@Override
		public Object[] getElements(Object inputElement) {
			return getChildren(inputElement);
		}

		@Override
		public Object[] getChildren(Object parentElement) {
			if (parentElement instanceof ASTVisitable) {
				return childrenProvider.getChildren((ASTVisitable) parentElement);
			}
			return new Object[0];
		}

		@Override
		public Object getParent(Object element) {
			return null;
		}

		@Override
		public boolean hasChildren(Object element) {
			return getChildren(element).length > 0;
		}
		
	}
	
	private class ASTContentVisitor implements ASTVisitor {
		
		private Object[] children = {};
		
		public Object[] getChildren(ASTVisitable visitable) {
			visitable.accept(this);
			return children;
		}

		@Override
		public void visit(Script script) {
			children = script.getStatements().toArray();
		}

		@Override
		public void visit(ActionDefinitionStatement statement) {
			List<Object> objects = new ArrayList<Object>();
//			objects.addAll(statement.getParameters());
			objects.addAll(statement.getStatements());
			children = objects.toArray();
		}

		@Override
		public void visit(ParameterDefinitionExpression expression) {
			children = new Object[0];
		}

		@Override
		public void visit(ErrorStatement statement) {
			children = new Object[0];
		}

		@Override
		public void visit(ActionCallStatement statement) {
			children = new Object[0];
		}

		@Override
		public void visit(IntegerValueExpression expression) {
			children = new Object[0];
		}

		@Override
		public void visit(StatementListValueExpression expression) {
			children = new Object[0];
		}

		@Override
		public void visit(ParameterValueExpression expression) {
			children = new Object[0];
		}
		
	}

}

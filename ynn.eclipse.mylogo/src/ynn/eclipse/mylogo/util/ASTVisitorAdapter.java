package ynn.eclipse.mylogo.util;

import ynn.mylogo.parser.ast.ASTVisitor;
import ynn.mylogo.parser.ast.AbstractStatement;
import ynn.mylogo.parser.ast.AbstractValueExpression;
import ynn.mylogo.parser.ast.ActionCallStatement;
import ynn.mylogo.parser.ast.ActionDefinitionStatement;
import ynn.mylogo.parser.ast.ErrorStatement;
import ynn.mylogo.parser.ast.IntegerValueExpression;
import ynn.mylogo.parser.ast.ParameterDefinitionExpression;
import ynn.mylogo.parser.ast.ParameterValueExpression;
import ynn.mylogo.parser.ast.Script;
import ynn.mylogo.parser.ast.StatementListValueExpression;

/**
 * An empty basic implementation of an {@link ASTVisitor}.<br/>
 * This implementation does nothing except for traversing the entire model. 
 * Each <code>visit</code> method does nothing except for visiting the sub-nodes. <br/>
 * It is recommended to call the <code>super</code>'s <code>visit</code> method when overriding a method.
 *  
 * @author I044999
 *
 */
public class ASTVisitorAdapter implements ASTVisitor {

	@Override
	public void visit(Script script) {
		for (AbstractStatement statement : script.getStatements()) {
			statement.accept(this);
		}
	}

	@Override
	public void visit(ActionDefinitionStatement statement) {
		for (ParameterDefinitionExpression parameter : statement.getParameters()) {
			parameter.accept(this);
		}
		for (AbstractStatement subStatement : statement.getStatements()) {
			subStatement.accept(this);
		}
	}

	@Override
	public void visit(ParameterDefinitionExpression expression) {
		// Do nothing
	}

	@Override
	public void visit(ErrorStatement statement) {
		// Do nothing
	}

	@Override
	public void visit(ActionCallStatement statement) {
		for (AbstractValueExpression argumentValue : statement.getArgumentValues()) {
			argumentValue.accept(this);
		}
	}

	@Override
	public void visit(IntegerValueExpression expression) {
		// Do nothing
	}

	@Override
	public void visit(StatementListValueExpression expression) {
		for (AbstractStatement statement : expression.getStatements()) {
			statement.accept(this);
		}
	}

	@Override
	public void visit(ParameterValueExpression expression) {
		// Do nothing
	}

}

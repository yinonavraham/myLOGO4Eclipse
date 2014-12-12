package ynn.eclipse.mylogo.editors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.resource.ColorRegistry;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContentAssistant;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.reconciler.DirtyRegion;
import org.eclipse.jface.text.reconciler.IReconciler;
import org.eclipse.jface.text.reconciler.IReconcilingStrategy;
import org.eclipse.jface.text.reconciler.Reconciler;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.ITokenScanner;
import org.eclipse.jface.text.rules.IWhitespaceDetector;
import org.eclipse.jface.text.rules.IWordDetector;
import org.eclipse.jface.text.rules.NumberRule;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.rules.WhitespaceRule;
import org.eclipse.jface.text.rules.WordRule;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;
import org.eclipse.jface.text.source.projection.ProjectionAnnotation;
import org.eclipse.jface.text.source.projection.ProjectionViewer;
import org.eclipse.jface.text.templates.DocumentTemplateContext;
import org.eclipse.jface.text.templates.Template;
import org.eclipse.jface.text.templates.TemplateContext;
import org.eclipse.jface.text.templates.TemplateContextType;
import org.eclipse.jface.text.templates.TemplateProposal;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.ui.IEditorPart;

import ynn.eclipse.mylogo.markers.MarkersHelper;
import ynn.eclipse.mylogo.util.ASTVisitorAdapter;
import ynn.mylogo.model.ActionsRegistry;
import ynn.mylogo.model.designtime.ActionDefinition;
import ynn.mylogo.model.designtime.ArgumentDefinition;
import ynn.mylogo.model.runtime.ActionList;
import ynn.mylogo.parser.LogoParser;
import ynn.mylogo.parser.LogoParser.ErrorEntry;
import ynn.mylogo.parser.LogoParser.ParserResult;
import ynn.mylogo.parser.ast.ASTVisitor;
import ynn.mylogo.parser.ast.ActionDefinitionStatement;

public class LogoScriptSourceViewerConfiguration extends SourceViewerConfiguration {
	
	private static final String COLOR_KEY_KEYWORD = "logo_keyword";
	private static final String COLOR_KEY_NUMBER = "logo_number";
	private static final String COLOR_KEY_PARAM = "logo_param";
	
	static {
		ColorRegistry colorRegistry = JFaceResources.getColorRegistry();
		if (!colorRegistry.hasValueFor(COLOR_KEY_KEYWORD)) colorRegistry.put(COLOR_KEY_KEYWORD, new RGB(127, 0, 85));
		if (!colorRegistry.hasValueFor(COLOR_KEY_NUMBER)) colorRegistry.put(COLOR_KEY_NUMBER, new RGB(0, 0, 0));
		if (!colorRegistry.hasValueFor(COLOR_KEY_PARAM)) colorRegistry.put(COLOR_KEY_PARAM, new RGB(0, 0, 192));
	}

	private IEditorPart editorPart;
	
	public LogoScriptSourceViewerConfiguration(IEditorPart editorPart) {
		this.editorPart = editorPart;
	}
	
	/* *******************
	 * Content assistant *
	 *********************/
	
	@Override
	public IContentAssistant getContentAssistant(final ISourceViewer sourceViewer) {
		ContentAssistant assistant = new ContentAssistant();
		IContentAssistProcessor processor = new IContentAssistProcessor() {

			private final String CONTEXT_ID = "logo_template_context";
			private final TemplateContextType CONTEXT_TYPE = new TemplateContextType(CONTEXT_ID, "LOGO Templates");
			private final Template TEMPLATE_ACTION_DEF = new Template(
					"ED", "Action definition", CONTEXT_ID, 
					"ED ${name} ${parameters}\n\t${actions}\nEND", 
					false);

			@Override
			public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset) {
				List<ICompletionProposal> proposals = new ArrayList<ICompletionProposal>();
				ActionsRegistry registry = new ActionsRegistry();
				String lastWord = lastWord(viewer.getDocument(), offset);
				int replacementOffset = offset - lastWord.length();
				int replacementLength = lastWord.length();
				IDocument document = sourceViewer.getDocument();
				TemplateContext context = new DocumentTemplateContext(CONTEXT_TYPE, document , replacementOffset, replacementLength);
				IRegion region = new Region(replacementOffset, replacementLength);
				Image image = null;
				for (ActionDefinition actionDefinition : registry.getActionDefinitions()) {
					String actionName = actionDefinition.getName();
					if (!actionName.toLowerCase().startsWith(lastWord.toLowerCase())) continue;
					String pattern = buildTemplatePattern(actionDefinition);
					Template template = new Template(actionName, actionDefinition.getSignature(), CONTEXT_ID, pattern, false);
					TemplateProposal proposal = new TemplateProposal(template, context, region, image);
					proposals.add(proposal);
				}
				proposals.add(new TemplateProposal(TEMPLATE_ACTION_DEF, context, region, image));
				return proposals.toArray(new ICompletionProposal[proposals.size()]);
			}
			
			private String buildTemplatePattern(ActionDefinition actionDefinition) {
				StringBuilder pattern = new StringBuilder();
				pattern.append(actionDefinition.getName());
				for (ArgumentDefinition arg : actionDefinition.getArguments()) {
					if (arg.getValueType() == ActionList.class) {
						pattern.append(" [${").append(arg.getName().substring(1)).append("}]");
					} else {
						pattern.append(" ${").append(arg.getName().substring(1)).append("}");
					}
				}
				return pattern.toString();
			}

			private String lastWord(IDocument doc, int offset) {
				try {
					for (int n = offset-1; n >= 0; n--) {
						char c = doc.getChar(n);
						if (!isLetter(c))
							return doc.get(n + 1, offset-n-1);
					}
				} catch (BadLocationException e) {
					//TODO log the exception
				}
				return "";
		      }

			@Override
			public IContextInformation[] computeContextInformation(ITextViewer viewer, int offset) {
				return null;
			}

			@Override
			public char[] getCompletionProposalAutoActivationCharacters() {
				return null;
			}

			@Override
			public char[] getContextInformationAutoActivationCharacters() {
				return null;
			}

			@Override
			public String getErrorMessage() {
				return null;
			}

			@Override
			public IContextInformationValidator getContextInformationValidator() {
				return null;
			}
			
		};
		assistant.setContentAssistProcessor(processor, IDocument.DEFAULT_CONTENT_TYPE);
		assistant.setInformationControlCreator(getInformationControlCreator(sourceViewer));
		assistant.enableAutoActivation(true);
		return assistant;
	}
	
	/* ***********************************
	 * Folding support and error markers *
	 *************************************/
	
	@Override
	public IReconciler getReconciler(final ISourceViewer sourceViewer) {
		Reconciler reconciler = new Reconciler();
		reconciler.setProgressMonitor(new NullProgressMonitor());
		IReconcilingStrategy strategy = new IReconcilingStrategy() {
			
			private IDocument document;
			private Annotation[] oldAnnotations = null;

			@Override
			public void setDocument(IDocument document) {
				this.document = document;
			}
			
			@Override
			public void reconcile(DirtyRegion dirtyRegion, IRegion subRegion) {
				IRegion region = new Region(0, document.getLength());
				reconcile(region);
			}
			
			@Override
			public void reconcile(IRegion partition) {
				String text = document.get();
				LogoParser parser = new LogoParser(new ActionsRegistry());
				ParserResult parserResult = parser.parse(text);
				
				// Folding support
				Annotation[] deletions = oldAnnotations;
				final Map<Annotation, Position> additions = new HashMap<Annotation, Position>();
				Annotation[] modifications = null;
				ASTVisitor visitor = new ASTVisitorAdapter() {
					@Override
					public void visit(ActionDefinitionStatement statement) {
						ynn.mylogo.parser.Token startToken = statement.getActionDefStartToken();
						ynn.mylogo.parser.Token endToken = statement.getActionDefEndToken();
						if (startToken != null && endToken != null) {
							ProjectionAnnotation annotation = new ProjectionAnnotation();
							int offset = startToken.getStart();
							int length = endToken.getStart() + endToken.getValue().length() - offset;
							Position position = new Position(offset, length);
							additions.put(annotation, position);
						}
						super.visit(statement);
					}
				};
				parserResult.getScript().accept(visitor);
				((ProjectionViewer)sourceViewer).getProjectionAnnotationModel().modifyAnnotations(deletions, additions, modifications);
				oldAnnotations = additions.keySet().toArray(new Annotation[0]);
				
				// Error markers
				IFile file = (IFile) editorPart.getEditorInput().getAdapter(IFile.class);
				MarkersHelper.deleteMarkers(file);
				List<ErrorEntry> errors = parserResult.getErrors();
				MarkersHelper.addMarkers(file, errors);
			}
		};
		reconciler.setReconcilingStrategy(strategy, IDocument.DEFAULT_CONTENT_TYPE);
		reconciler.install(sourceViewer);
		return reconciler;
	}
	
	/* *****************
	 * Syntax coloring *
	 *******************/

	@Override
	public IPresentationReconciler getPresentationReconciler(ISourceViewer sourceViewer) {
		PresentationReconciler reconciler = new PresentationReconciler();
		ITokenScanner tokenScanner = createTokenScanner();
		DefaultDamagerRepairer defaultDamagerRepairer = new DefaultDamagerRepairer(tokenScanner);
		reconciler.setDamager(defaultDamagerRepairer, IDocument.DEFAULT_CONTENT_TYPE);
		reconciler.setRepairer(defaultDamagerRepairer, IDocument.DEFAULT_CONTENT_TYPE);
		return reconciler;
	}

	private ITokenScanner createTokenScanner() {
		RuleBasedScanner scanner = new RuleBasedScanner();
		scanner.setRules(createRules());
		return scanner;
	}

	private IRule[] createRules() {
		ColorRegistry colorRegistry = JFaceResources.getColorRegistry();
		IToken tokenKeyword = new Token(new TextAttribute(colorRegistry.get(COLOR_KEY_KEYWORD), null, SWT.BOLD));
		IToken tokenNumber = new Token(new TextAttribute(colorRegistry.get(COLOR_KEY_NUMBER)));
		IToken tokenParam = new Token(new TextAttribute(colorRegistry.get(COLOR_KEY_PARAM)));
		IToken tokenName = new Token(new TextAttribute(null));
		return new IRule[] {
				createKeywordRule(tokenKeyword),
				new NumberRule(tokenNumber),
				createParamRule(tokenParam),
				createWhitespaceRule(),
				createNameRule(tokenName)
				};
	}

	private IRule createWhitespaceRule() {
		IWhitespaceDetector detector = new IWhitespaceDetector() {
			@Override
			public boolean isWhitespace(char c) {
				return Character.isWhitespace(c);
			}
		};
		return new WhitespaceRule(detector);
	}

	private IRule createParamRule(IToken token) {
		IWordDetector detector = new IWordDetector() {
			@Override
			public boolean isWordStart(char c) {
				return c == ':';
			}
			
			@Override
			public boolean isWordPart(char c) {
				return isLetter(c) || isDigit(c);
			}
		};
		WordRule rule = new WordRule(detector, token, true);
		return rule;
	}

	private IRule createKeywordRule(IToken token) {
		IWordDetector detector = createNameDetector();
		WordRule rule = new WordRule(detector, Token.UNDEFINED, true);
		// Add all predefined keywords
		rule.addWord("ed", token);
		rule.addWord("end", token);
		// Add all predefined actions
		ActionsRegistry registry = new ActionsRegistry();
		for (ActionDefinition actionDefinition : registry.getActionDefinitions()) {
			rule.addWord(actionDefinition.getName(), token);
		};
		return rule;
	}

	private IRule createNameRule(IToken token) {
		IWordDetector detector = createNameDetector();
		WordRule rule = new WordRule(detector, token, true);
		return rule;
	}

	private IWordDetector createNameDetector() {
		IWordDetector detector = new IWordDetector() {
			@Override
			public boolean isWordStart(char c) {
				return isLetter(c);
			}
			
			@Override
			public boolean isWordPart(char c) {
				return isLetter(c) || isDigit(c);
			}
		};
		return detector;
	}
	
	/* ****************
	 * Helper methods *
	 ******************/

	private boolean isDigit(char c) {
		return c >= '0' && c <= '9';
	}

	private boolean isLetter(char c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
	}
	
}

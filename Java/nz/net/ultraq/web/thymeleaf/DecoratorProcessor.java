
package nz.net.ultraq.web.thymeleaf;

import static nz.net.ultraq.web.thymeleaf.LayoutDialect.LAYOUT_PREFIX;

import java.util.List;
import java.util.Map;

import org.thymeleaf.Arguments;
import org.thymeleaf.Template;
import org.thymeleaf.TemplateProcessingParameters;
import org.thymeleaf.dom.Document;
import org.thymeleaf.dom.Element;
import org.thymeleaf.dom.Node;
import org.thymeleaf.dom.Text;
import org.thymeleaf.fragment.FragmentAndTarget;
import org.thymeleaf.processor.ProcessorResult;
import org.thymeleaf.standard.fragment.StandardFragmentProcessor;

/**
 * Processor for the 'layout:decorator' attribute.  Locates the page identified
 * by the attribute and decorates the current page with it.
 * <p>
 * The page identified by the <tt>layout:decorator</tt> attribute is resolved
 * using the same template resolver as Thymeleaf, so be sure to locate your
 * decorator pages the same way you would your Thymeleaf page fragments.
 * 
 * @author Emanuel Rabina
 */
public class DecoratorProcessor extends AbstractContentProcessor {

	private static final String LINE_SEPARATOR = System.getProperty("line.separator");

	private static final String HTML_ELEMENT_HTML  = "html";
	private static final String HTML_ELEMENT_HEAD  = "head";
	private static final String HTML_ELEMENT_TITLE = "title";
	private static final String HTML_ELEMENT_BODY  = "body";

	static final String ATTRIBUTE_NAME_DECORATOR = "decorator";
	static final String ATTRIBUTE_NAME_DECORATOR_FULL = LAYOUT_PREFIX + ":" + ATTRIBUTE_NAME_DECORATOR;

	/**
	 * Constructor, sets this processor to work on the 'decorator' attribute.
	 */
	public DecoratorProcessor() {

		super(ATTRIBUTE_NAME_DECORATOR);
	}

	/**
	 * Decorate the BODY part.  This step merges the decorator and page BODY
	 * attributes, ensuring only that a BODY element actually exists in the
	 * result.  The bulk of the body decoration is actually performed by the
	 * fragment replacements.
	 * 
	 * @param decoratorhtml Decorator's HTML element.
	 * @param pagebody		Page's BODY element.
	 */
	private void decorateBody(Element decoratorhtml, Element pagebody) {

		// If the page has no BODY, then we don't need to do anything
		if (pagebody == null) {
			return;
		}

		// If the decorator has no BODY, we can just copy the page BODY
		Element decoratorbody = findElement(decoratorhtml, HTML_ELEMENT_BODY);
		if (decoratorbody == null) {
			decoratorhtml.addChild(pagebody);
			decoratorhtml.addChild(new Text(LINE_SEPARATOR));
			return;
		}

		mergeAttributes(pagebody, decoratorbody);
	}

	/**
	 * Decorate the HEAD part.  This step replaces the decorator's TITLE element
	 * if the page has one, and appends all other page elements to the HEAD
	 * section, after all the decorator elements.
	 * 
	 * @param decoratorhtml Decorator's HTML element.
	 * @param pagehead		Page's HEAD element.
	 */
	private void decorateHead(Element decoratorhtml, Element pagehead) {

		// If the page has no HEAD, then we don't need to do anything
		if (pagehead == null) {
			return;
		}

		// If the decorator has no HEAD, then we can just copy the page HEAD
		Element decoratorhead = findElement(decoratorhtml, HTML_ELEMENT_HEAD);
		if (decoratorhead == null) {
			decoratorhtml.insertChild(0, new Text(LINE_SEPARATOR));
			decoratorhtml.insertChild(1, pagehead);
			return;
		}

		// Append the page's HEAD elements to the end of the decorator's HEAD section,
		// replacing the decorator's TITLE element if necessary
		Element pagetitle = findElement(pagehead, HTML_ELEMENT_TITLE);
		if (pagetitle != null) {
			pagehead.removeChild(pagetitle);
			Element decoratortitle = findElement(decoratorhead, HTML_ELEMENT_TITLE);
			if (decoratortitle != null) {
				decoratorhead.insertBefore(decoratortitle, pagetitle);
				decoratorhead.removeChild(decoratortitle);
			}
			else {
				decoratorhead.insertChild(0, new Text(LINE_SEPARATOR));
				decoratorhead.insertChild(1, pagetitle);
			}
		}
		for (Node pageheadnode: pagehead.getChildren()) {
			decoratorhead.addChild(pageheadnode);
		}

		mergeAttributes(pagehead, decoratorhead);
	}

	/**
	 * Recursive search for an element within the given node in the DOM tree.
	 * 
	 * @param element Node to initiate the search from.
	 * @param name	  Name of the element to look for.
	 * @return Element with the given name, or <tt>null</tt> if the element
	 * 		   could not be found.
	 */
	private Element findElement(Element element, String name) {

		if (element.getOriginalName().equals(name)) {
			return element;
		}
		for (Element child: element.getElementChildren()) {
			Element result = findElement(child, name);
			if (result != null) {
				return result;
			}
		}
		return null;
	}

	/**
	 * Locates the decorator page specified by the layout attribute and applies
	 * it to the current page being processed.
	 * 
	 * @param arguments
	 * @param element
	 * @param attributeName
	 * @return Result of the processing.
	 */
	@Override
	protected ProcessorResult processAttribute(Arguments arguments, Element element, String attributeName) {

		// Ensure the decorator attribute is in the root element of the document
		if (!(element.getParent() instanceof Document)) {
			throw new IllegalArgumentException("layout:decorator attribute must appear in the root element of your content page");
		}

		// Locate the decorator page, ensure it has an HTML root element
		FragmentAndTarget fragmentandtarget = StandardFragmentProcessor.computeStandardFragmentSpec(
				arguments.getConfiguration(), arguments, element.getAttributeValue(attributeName),
				null, null);
		Template decorator = arguments.getTemplateRepository().getTemplate(new TemplateProcessingParameters(
				arguments.getConfiguration(), fragmentandtarget.getTemplateName(), arguments.getContext()));
		Element decoratorhtmlelement = decorator.getDocument().getFirstElementChild();
		if (decoratorhtmlelement == null || !decoratorhtmlelement.getOriginalName().equals(HTML_ELEMENT_HTML)) {
			throw new IllegalArgumentException("Decorator page " + decorator.getTemplateName() + " must have an <html> root element");
		}

		// Thymeleaf's template repository already returns clones of templates, so the
		// template resolution above returned a clone of the decorator page.  The following
		// functions operate on the decorator directly.

		element.removeAttribute(attributeName);
		decorateHead(decoratorhtmlelement, findElement(element, HTML_ELEMENT_HEAD));
		decorateBody(decoratorhtmlelement, findElement(element, HTML_ELEMENT_BODY));

		// Gather all fragment parts from this page and scope to the HTML element.  These
		// will be used to decorate the BODY as Thymeleaf encounters the fragment placeholders.
		Map<String,Object> fragments = findFragments(element.getParent().getElementChildren());
		if (!fragments.isEmpty()) {
			decoratorhtmlelement.setAllNodeLocalVariables(fragments);
		}

		// Pull the decorator page into this document
		if (element.getOriginalName().equals(HTML_ELEMENT_HTML)) {
			mergeAttributes(element, decoratorhtmlelement);
		}
		Document decoratordocument = (Document)decoratorhtmlelement.getParent();
		if (decoratordocument.hasDocType()) {
			((Document)element.getParent()).setDocType(decoratordocument.getDocType());
		}
		List<Node> nodes = decoratordocument.getChildren();

		for (Node node: nodes){
		    if (node.equals(decoratorhtmlelement)) {
		        break;
		    }
		    ((Document)element.getParent()).insertBefore(element.getParent().getFirstElementChild(), node);
		    
		}
		    
		pullTargetContent(element, decoratorhtmlelement);

		return ProcessorResult.OK;
	}
}

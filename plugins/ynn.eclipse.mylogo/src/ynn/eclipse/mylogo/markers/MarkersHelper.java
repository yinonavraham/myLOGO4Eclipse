package ynn.eclipse.mylogo.markers;

import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;

import ynn.mylogo.parser.Token;
import ynn.mylogo.parser.LogoParser.ErrorEntry;

public class MarkersHelper {
	
	private static final String MARKER_TYPE = "ynn.eclipse.mylogo.logoProblem";
	
	private MarkersHelper() {}

	public static void addMarkers(IFile file, List<ErrorEntry> errors) {
		for (ErrorEntry entry : errors) {
			Token token = entry.getToken();
			int length = token.getValue().length();
			addMarker(file, entry.getMessage(), token.getRow()+1, token.getStart(), length, IMarker.SEVERITY_ERROR);
		}
	}

	private static void addMarker(IFile file, String message, int lineNumber, int start, int length, int severity) {
		try {
			IMarker marker = file.createMarker(MARKER_TYPE);
			marker.setAttribute(IMarker.MESSAGE, message);
			marker.setAttribute(IMarker.SEVERITY, severity);
			if (lineNumber == -1) {
				lineNumber = 1;
			}
			marker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
			marker.setAttribute(IMarker.CHAR_START, start);
			marker.setAttribute(IMarker.CHAR_END, start+length);
		} catch (CoreException e) {
			//TODO Handle exception
		}
	}
	
	public static void deleteMarkers(IProject project) throws CoreException {
		project.deleteMarkers(MARKER_TYPE, true, IResource.DEPTH_INFINITE);
	}

	public static void deleteMarkers(IFile file) {
		try {
			file.deleteMarkers(MARKER_TYPE, false, IResource.DEPTH_ZERO);
		} catch (CoreException ce) {
			//TODO Handle exception
		}
	}

}

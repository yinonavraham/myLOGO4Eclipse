package ynn.eclipse.mylogo.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;

public class Util {
	
	private Util() {}

	public static String readFileContent(IFile file) throws IOException, CoreException {
		InputStream inputStream = file.getContents();
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream ));
			StringWriter strWriter = new StringWriter();
			BufferedWriter writer = new BufferedWriter(strWriter);
			boolean first = true;
			String line;
			while ((line = reader.readLine()) != null) {
				if (first) first = false;
				else writer.newLine();
				writer.write(line);
			}
			writer.close();
			return strWriter.toString();
		} finally {
			inputStream.close();
		}
	}

}

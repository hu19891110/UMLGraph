/*
 * Create a graphviz graph based on the classes in the specified java
 * source files.
 *
 * (C) Copyright 2002, 2003 Diomidis Spinellis
 * 
 * Permission to use, copy, and distribute this software and its
 * documentation for any purpose and without fee is hereby granted,
 * provided that the above copyright notice appear in all copies and that
 * both that copyright notice and this permission notice appear in
 * supporting documentation.
 * 
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND WITHOUT ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTIES OF
 * MERCHANTIBILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 *
 * $Id$
 *
 */

import com.sun.javadoc.*;
import java.io.*;
import java.lang.*;
import java.util.*;

/**
 * Represent the program options
 */
class Options implements Cloneable {
	PrintWriter w;
	boolean showQualified;
	boolean showAttributes;
	boolean showOperations;
	boolean showVisibility;
	boolean horizontal;
	boolean showType;
	String edgeFontName;
	String edgeFontColor;
	String edgeColor;
	double edgeFontSize;
	String nodeFontName;
	String nodeFontAbstractName;
	String nodeFontColor;
	double nodeFontSize;
	String nodeFillColor;
	String bgColor;

	Options() {
		showQualified = false;
		showAttributes = false;
		showOperations = false;
		showVisibility = false;
		showType = false;
		edgeFontName = "Helvetica";
		edgeFontColor = "black";
		edgeColor = "black";
		edgeFontSize = 10;
		nodeFontName = "Helvetica";
		nodeFontAbstractName = "Helvetica-Oblique";
		nodeFontColor = "black";
		nodeFontSize = 10;
		nodeFillColor = null;
		bgColor = null;
	}

	public Object clone() 
	{
		Object o = null;
		try {
			o = super.clone();
		} catch (CloneNotSupportedException e) {
			// Should not happen
		} 
		return o;
	}

	/** Most verbose output */
	public void setAll() {
		showAttributes = true;
		showOperations = true;
		showVisibility = true;
		showType = true;
	}

	/** Set the options based on a lingle option and its arguments */
	private void setOption(String[] opt) {
		if(opt[0].equals("-qualify")) {
			showQualified = true;
		} else if(opt[0].equals("-horizontal")) {
			horizontal = true;
		} else if(opt[0].equals("-attributes")) {
			showAttributes = true;
		} else if(opt[0].equals("-operations")) {
			showOperations = true;
		} else if(opt[0].equals("-visibility")) {
			showVisibility = true;
		} else if(opt[0].equals("-types")) {
			showType = true;
		} else if(opt[0].equals("-all")) {
			setAll();
		} else if(opt[0].equals("-bgcolor")) {
			bgColor = opt[1];
		} else if(opt[0].equals("-edgecolor")) {
			edgeColor = opt[1];
		} else if(opt[0].equals("-edgefontcolor")) {
			edgeFontColor = opt[1];
		} else if(opt[0].equals("-edgefontname")) {
			edgeFontName = opt[1];
		} else if(opt[0].equals("-edgefontsize")) {
			edgeFontSize = Integer.parseInt(opt[1]);
		} else if(opt[0].equals("-nodefontcolor")) {
			nodeFontColor = opt[1];
		} else if(opt[0].equals("-nodefontname")) {
			nodeFontName = opt[1];
		} else if(opt[0].equals("-nodefontabstractname")) {
			nodeFontAbstractName = opt[1];
		} else if(opt[0].equals("-nodefontsize")) {
			nodeFontSize = Integer.parseInt(opt[1]);
		} else if(opt[0].equals("-nodefillcolor")) {
			nodeFillColor = opt[1];
		}
	}

	/** Set the options based on the command line parameters */
	public void setOptions(String[][] options) {
		for (int i = 0; i < options.length; i++)
			setOption(options[i]);
	}


	/** Set the options based on the tag elements of the ClassDoc parameter */
	public void setOptions(ClassDoc p) {
		if (p == null)
			return;

		Tag tags[] = p.tags("opt");
		for (int i = 0; i < tags.length; i++) {
			String[] opt = StringFuns.tokenize(tags[i].text());
			opt[0] = "-" + opt[0];
			setOption(opt);
		}
	}

	public void openFile() throws IOException, UnsupportedEncodingException {
		FileOutputStream fos = new FileOutputStream("graph.dot");
		w = new PrintWriter(new BufferedWriter(new OutputStreamWriter(fos)));
	}
}

/**
 * Class's dot-comaptible alias name (for fully qualified class names)
 * and printed information
 */
class ClassInfo {
	private static int classnum;
	/** Alias name for the class */
	String name;
	/** True if the class class node has been printed */
	boolean nodePrinted;

	ClassInfo(boolean p) {
		nodePrinted = p;
		name = "c" + (new Integer(classnum)).toString();
		classnum++;
	}
}

/** String utility functions */
class StringFuns {
	/** Guillemot left (open) */
	public static final char guilopen = (char)0xab;
	/** Guillemot right (close) */
	public static final char guilclose = (char)0xbb;

	/** Tokenize string s into an array */
	public static String[] tokenize(String s) {
		ArrayList r = new ArrayList();
		String remain = s, tok;
		int n = 0, pos;

		remain.trim();
		while (remain.length() > 0) {
			if (remain.startsWith("\"")) {
				// Field in quotes
				pos = remain.indexOf('"', 1);
				if (pos == -1)
					break;
				r.add(remain.substring(1, pos));
				if (pos + 1 < remain.length())
					pos++;
			} else {
				// Space-separated field
				pos = remain.indexOf(' ', 0);
				if (pos == -1) {
					r.add(remain);
					remain = "";
				} else
					r.add(remain.substring(0, pos));
			}
			remain = remain.substring(pos + 1);
			remain.trim();
			// - is used as a placeholder for empy fields
			if (r.get(n).equals("-"))
				r.set(n, "");
			n++;
		}
		return ((String[])(r.toArray(new String[0])));
	}

	/** Convert < and > characters in the string to the respective guillemot characters */
	public static String guillemize(String s) {
		StringBuffer r = new StringBuffer(s);

		for (int i = 0; i < r.length(); i++)
			switch (r.charAt(i)) {
			case '<':
				r.setCharAt(i, guilopen);
				break;
			case '>':
				r.setCharAt(i, guilclose);
				break;
			}
		return r.toString();
	}
}

/**
 * Class graph generation engine
 */
class ClassGraph {
	private static HashMap classnames = new HashMap();
	private Options opt;

	/** 
	 * Print the visibility adornment of element e prefixed by
	 * any stereotypes
	 */
	private void visibility(ProgramElementDoc e) {
		opt.w.print(stereotype(e, 'l'));
		if (!opt.showVisibility)
			return;
		if (e.isPrivate())
			opt.w.print('-');
		if (e.isPublic())
			opt.w.print('+');
		if (e.isProtected())
			opt.w.print('#');
		opt.w.print(' ');
	}

	/** Print the method parameter p */
	private void parameter(Parameter p[]) {
		for (int i = 0; i < p.length; i++) {
			opt.w.print(p[i].name());
			type(p[i].type());
			if (i + 1 < p.length)
				opt.w.print(", ");
		}
	}

	/** Print the type t */
	private void type(Type t) {
		if (t.typeName().equals("void"))
			return;
		opt.w.print(" : ");
		if (opt.showQualified)
			opt.w.print(t.qualifiedTypeName());
		else
			opt.w.print(t.typeName());
		opt.w.print(t.dimension());
	}

	/** Print the class's attributes f */
	private void attributes(FieldDoc f[]) {
		for (int i = 0; i < f.length; i++) {
			if (hidden(f[i]))
				continue;
			visibility(f[i]);
			opt.w.print(f[i].name());
			if (opt.showType)
				type(f[i].type());
			opt.w.print("\\l");
			opt.w.print(tagvalue(f[i], "", 'r'));
		}
	}

	/** Print the class's operations m */
	private void operations(MethodDoc m[]) {
		for (int i = 0; i < m.length; i++) {
			if (hidden(m[i]))
				continue;
			visibility(m[i]);
			opt.w.print(m[i].name());
			if (opt.showType) {
				opt.w.print("(");
				parameter(m[i].parameters());
				opt.w.print(")");
				type(m[i].returnType());
			} else
				opt.w.print("()");
			opt.w.print("\\l");
			opt.w.print(tagvalue(m[i], "", 'r'));
		}
	}

	/** 
	 * Return as a string the tagged values associated with c 
	 * @param c the Doc entry to look for @tagvalue
	 * @param prevterm the termination string for the previous element
	 * @param term the termination character for each tagged value
	 */
	private static String tagvalue(Doc c, String prevterm, char term) {
		String r;
		Tag tags[] = c.tags("tagvalue");
		if (tags.length > 0)
			r = prevterm;
		else
			r = "";
		for (int i = 0; i < tags.length; i++) {
			String t[] = StringFuns.tokenize(tags[i].text());
			if (t.length != 2) {
				System.err.println("@tagvalue expects two fields: " + tags[i].text());
				return ("");
			}
			r += "\\{" + t[0] + " = " + t[1] + "\\}\\"  + term;
		}
		return (r);
	}

	/** 
	 * Return as a string the stereotypes associated with c 
	 * terminated by the escape character term 
	 */
	private static String stereotype(Doc c, char term) {
		String r = "";
		Tag tags[] = c.tags("stereotype");
		for (int i = 0; i < tags.length; i++) {
			String t[] = StringFuns.tokenize(tags[i].text());
			if (t.length != 1) {
				System.err.println("@stereotype expects one field: " + tags[i].text());
				return ("");
			}
			r += StringFuns.guilopen + t[0] + StringFuns.guilclose + '\\' + term;
		}
		return (r);
	}

	/** Return true if c has a @hidden tag associated with it */
	private static boolean hidden(Doc c) {
		Tag tags[] = c.tags("hidden");
		return (tags.length > 0);
	}

	/** Return a class's internal name */
	private static String name(String c) {
		ClassInfo ci;

		if ((ci = (ClassInfo)classnames.get(c)) == null)
			classnames.put(c, ci = new ClassInfo(false));
		return ci.name;
	}

	/** Return a class's internal name, printing the class if needed */
	private String name(ClassDoc c) {
		ClassInfo ci;
		boolean toPrint;

		if ((ci = (ClassInfo)classnames.get(c.toString())) != null)
			toPrint = !ci.nodePrinted;
		else {
			toPrint = true;
			classnames.put(c.toString(), ci = new ClassInfo(true));
		}
		if (toPrint && !hidden(c)) {
			// Associate classnames alias
			String r = c.toString();
			opt.w.println("\t// " + r);
			if (!opt.showQualified) {
				// Create readable string by stripping leading path
				int dotpos = r.lastIndexOf('.');
				if (dotpos != -1)
					r = r.substring(dotpos + 1, r.length());
			}
			// Create label
			opt.w.print("\t" + ci.name + " [");
			r = stereotype(c, 'n') + r;
			if (c.isInterface())
				r = StringFuns.guilopen + "interface" + StringFuns.guilclose + "\\n" + r;
			boolean showMembers = 
				(opt.showAttributes || opt.showOperations) &&
				(c.methods().length > 0 || c.fields().length > 0);
			r += tagvalue(c, "\\n", 'r');
			if (showMembers)
				opt.w.print("label=\"{" + r + "\\n|");
			else
				opt.w.print("label=\"" + r + "\"");
			if (opt.showAttributes)
				attributes(c.fields());
			if (showMembers)
				opt.w.print("|");
			if (opt.showAttributes)
				operations(c.methods());
			if (showMembers)
				opt.w.print("}\"");
			// Use ariali [sic] for gif output of abstract classes
			opt.w.print(", fontname=\"" + 
				(c.isAbstract() ? 
				 opt.nodeFontAbstractName :
				 opt.nodeFontName) + "\"");
			if (opt.nodeFillColor != null)
				opt.w.print(", style=filled, fillcolor=\"" + opt.nodeFillColor + "\"");
			opt.w.print(", fontcolor=\"" + opt.nodeFontColor + "\"");
			opt.w.print(", fontsize=" + opt.nodeFontSize);
			opt.w.print(", URL=\"" + c.toString() + ".html\"");
			opt.w.println("];");
			ci.nodePrinted = true;
		}
		return ci.name;
	}


	/** 
	 * Print all relations for a given's class's tag
	 * @param tagname the tag containing the given relation
	 * @param from the source class
	 * @param name the source class internal name
	 * @param edgetype the dot edge specification
	 */
	private void relation(String tagname, Doc from, String name, String edgetype) {
		Tag tags[] = from.tags(tagname);
		for (int i = 0; i < tags.length; i++) {
			String t[] = StringFuns.tokenize(tags[i].text());	// l-src label l-dst target
			if (t.length != 4)
				System.err.println("Expected four fields: " + tags[i].text());
			opt.w.println("\t// " + from + " " + tagname + " " + t[3]);
			opt.w.println("\t" + name + " -> " + name(t[3]) + " [" +
				"taillabel=\"" + t[0] + "\", " + 
				"label=\"" + StringFuns.guillemize(t[1]) + "\", " + 
				"headlabel=\"" + t[2] + "\", " + 
				"fontname=\"" + opt.edgeFontName + "\", " + 
				"fontcolor=\"" + opt.edgeFontColor + "\", " + 
				"fontsize=" + opt.edgeFontSize + ", " + 
				"color=\"" + opt.edgeColor + "\", " + 
				edgetype + "]"
			);
		}
	}

	/** Print a class */
	public void print(Options iopt, ClassDoc c) {
		opt = (Options)iopt.clone();
		// Process class-local options (through @opt tags)
		opt.setOptions(c);
		String cs = name(c);
		// Print generalization (through the Java superclass)
		ClassDoc s = c.superclass();
		if (s != null && !s.toString().equals("java.lang.Object")) {
			opt.w.println("\t//" + c + " extends " + s);
			opt.w.println("\t" + name(s) + " -> " + cs + " [dir=back,arrowtail=empty];");
		}
		// Print generalizations (through @extends tags)
		Tag tags[] = c.tags("extends");
		for (int i = 0; i < tags.length; i++) {
			opt.w.println("\t//" + c + " extends " + tags[i].text());
			opt.w.println("\t" + name(tags[i].text()) + " -> " + cs + " [dir=back,arrowtail=empty];");
		}
		// Print realizations (Java interfaces)
		ClassDoc ifs[] = c.interfaces();
		for (int i = 0; i < ifs.length; i++) {
			opt.w.print("\t" + name(ifs[i]) + " -> " + cs + " [dir=back,arrowtail=empty,style=dashed];");
			opt.w.println("\t//" + c + " implements " + ifs[i]);
		}
		// Print other associations
		relation("assoc", c, cs, "arrowhead=none");
		relation("navassoc", c, cs, "arrowhead=open");
		relation("has", c, cs, "arrowhead=none, arrowtail=ediamond");
		relation("composed", c, cs, "arrowhead=none, arrowtail=diamond");
		relation("depend", c, cs, "arrowhead=open, style=dashed");
	}
}

/** Doclet API implementation */
public class UmlGraph {
	private static Options opt = new Options();

	/** Entry point */
	public static boolean start(RootDoc root)
                            throws IOException, UnsupportedEncodingException {
		opt.openFile();
		opt.setOptions(root.options());
		opt.setOptions(root.classNamed("UMLOptions"));
		prologue();
		ClassDoc[] classes = root.classes();
		ClassGraph c = new ClassGraph();
		for (int i = 0; i < classes.length; i++) {
			c.print(opt, classes[i]);
		}
		epilogue();
		return true;
	}

	/** Option checking */
	public static int optionLength(String option) {
		if(option.equals("-qualify") ||
		   option.equals("-horizontal") ||
		   option.equals("-attributes") ||
		   option.equals("-operations") ||
		   option.equals("-visibility") ||
		   option.equals("-types") ||
		   option.equals("-all"))
			return 1;
		else if(option.equals("-nodefillcolor") ||
		   option.equals("-nodefontcolor") ||
		   option.equals("-nodefontsize") ||
		   option.equals("-nodefontname") ||
		   option.equals("-nodefontabstractname") ||
		   option.equals("-edgefontcolor") ||
		   option.equals("-edgecolor") ||
		   option.equals("-edgefontsize") ||
		   option.equals("-edgefontname") ||
		   option.equals("-bgcolor"))
			return 2;
		else
			return 0;
	}

	/** Dot prologue */
	private static void prologue() {
		opt.w.println(
			"#!/usr/local/bin/dot\n" +
			"#\n" +
			"# Class diagram \n" +
			"# Generated by $Id$\n" +
			"#\n\n" +
			"digraph G {\n" +
			"\tedge [fontname=\"Helvetica\",fontsize=10,labelfontname=\"Helvetica\",labelfontsize=10];\n" +

			"\tnode [fontname=\"Helvetica\",fontsize=10,shape=record];"
		);
		if (opt.horizontal)
			opt.w.println("\trankdir=LR;\n\tranksep=1;");
		if (opt.bgColor != null)
			opt.w.println("\tbgcolor=\"" + opt.bgColor + "\";\n");
	}

	/** Dot epilogue */
	private static void epilogue() {
		opt.w.println("}\n");
		opt.w.flush();
	}
}

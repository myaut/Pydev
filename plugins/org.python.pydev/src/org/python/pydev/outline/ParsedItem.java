/**
 * Copyright (c) 2005-2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Eclipse Public License (EPL).
 * Please see the license.txt included with this distribution for details.
 * Any modifications to this file must keep this entire header intact.
 */
/*
 * Created on Jun 10, 2006
 * @author Fabio
 */
package org.python.pydev.outline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.eclipse.swt.graphics.Image;
import org.python.pydev.core.docutils.StringUtils;
import org.python.pydev.parser.ErrorDescription;
import org.python.pydev.parser.jython.SimpleNode;
import org.python.pydev.parser.jython.ast.Attribute;
import org.python.pydev.parser.jython.ast.ClassDef;
import org.python.pydev.parser.jython.ast.FunctionDef;
import org.python.pydev.parser.jython.ast.If;
import org.python.pydev.parser.jython.ast.Import;
import org.python.pydev.parser.jython.ast.ImportFrom;
import org.python.pydev.parser.jython.ast.Name;
import org.python.pydev.parser.jython.ast.NameTok;
import org.python.pydev.parser.jython.ast.aliasType;
import org.python.pydev.parser.jython.ast.commentType;
import org.python.pydev.parser.jython.ast.decoratorsType;
import org.python.pydev.parser.visitors.NodeUtils;
import org.python.pydev.parser.visitors.scope.ASTEntry;
import org.python.pydev.parser.visitors.scope.ASTEntryWithChildren;
import org.python.pydev.plugin.PydevPlugin;
import org.python.pydev.shared_core.string.FastStringBuffer;
import org.python.pydev.shared_ui.ImageCache;
import org.python.pydev.ui.UIConstants;

public class ParsedItem implements Comparable<Object> {

    private ParsedItem parent;
    private ParsedItem[] children;
    private ASTEntryWithChildren astThis; //may be null if root
    private ASTEntryWithChildren[] astChildrenEntries;
    private ErrorDescription errorDesc;

    /**
     * Constructor for a child with valid ast.
     */
    public ParsedItem(ParsedItem parent, ASTEntryWithChildren root, ASTEntryWithChildren[] astChildren) {
        this(astChildren, null);
        this.parent = parent;
        this.astThis = root;
    }

    /**
     * Constructor for a child with error.
     */
    public ParsedItem(ParsedItem parent, ErrorDescription errorDesc) {
        this.parent = parent;
        this.setErrorDesc(errorDesc);
    }

    /**
     * Constructor for the root.
     */
    public ParsedItem(ASTEntryWithChildren[] astChildren, ErrorDescription errorDesc) {
        this.astChildrenEntries = astChildren;
        this.setErrorDesc(errorDesc);
    }

    public ASTEntryWithChildren getAstThis() {
        return astThis;
    }

    public void setAstThis(ASTEntryWithChildren astThis) {
        this.setAstThis(astThis, null);
    }

    public void setAstThis(ASTEntryWithChildren astThis, ASTEntryWithChildren[] astChildrenEntries) {
        this.toStringCache = null;
        this.astThis = astThis;

        if (astChildrenEntries != null) {
            this.astChildrenEntries = astChildrenEntries;
            this.children = null; //the children must be recalculated...
        }
    }

    public ASTEntryWithChildren[] getAstChildrenEntries() {
        return astChildrenEntries;
    }

    public ErrorDescription getErrorDesc() {
        return errorDesc;
    }

    public void setErrorDesc(ErrorDescription errorDesc) {
        if (this.errorDesc == null && errorDesc == null) {
            return; // don't clear the caches
        }
        this.toStringCache = null;
        this.errorDesc = errorDesc;
    }

    private static final int QUALIFIER_PUBLIC = 0;
    private static final int QUALIFIER_PROTECTED = 1;
    private static final int QUALIFIER_PRIVATE = 2;

    private static int qualifierFromName(String name) {
        if (name.startsWith("__")) {
            if (!name.endsWith("__")) {
                return QUALIFIER_PRIVATE;

            } else {
                return QUALIFIER_PUBLIC;
            }
        } else if (name.startsWith("_")) {
            return QUALIFIER_PROTECTED;
        } else {
            return QUALIFIER_PUBLIC;
        }

    }

    // returns images based upon element type
    public Image getImage() {
        ImageCache imageCache = PydevPlugin.getImageCache();
        if (astThis == null) {
            return imageCache.get(UIConstants.ERROR);
        }

        SimpleNode token = astThis.node;
        return getImageForNode(imageCache, token, astThis.parent);
    }

    public static Image getImageForNode(ImageCache imageCache, SimpleNode token, ASTEntry parent) {
        if (token instanceof ClassDef) {
            String className = NodeUtils.getNameFromNameTok((NameTok) ((ClassDef) token).name);
            switch (qualifierFromName(className)) {
                case QUALIFIER_PROTECTED:
                    return imageCache.getImageDecorated(UIConstants.CLASS_ICON, UIConstants.PROTECTED_ICON,
                            ImageCache.DECORATION_LOCATION_BOTTOM_RIGHT);
                case QUALIFIER_PRIVATE:
                    return imageCache.getImageDecorated(UIConstants.CLASS_ICON, UIConstants.PRIVATE_ICON,
                            ImageCache.DECORATION_LOCATION_BOTTOM_RIGHT);
                default:
                    return imageCache.get(UIConstants.CLASS_ICON);
            }
        } else if (token instanceof FunctionDef) {
            FunctionDef functionDefToken = (FunctionDef) token;

            String methodName = NodeUtils.getNameFromNameTok((NameTok) ((FunctionDef) token).name);
            String qualifierIcon = null;
            switch (qualifierFromName(methodName)) {
                case QUALIFIER_PRIVATE:
                    qualifierIcon = UIConstants.PRIVATE_ICON;
                    break;
                case QUALIFIER_PROTECTED:
                    qualifierIcon = UIConstants.PROTECTED_ICON;
                    break;
            }
            String decorationIcon = null;
            if (functionDefToken.decs != null) {
                for (decoratorsType decorator : functionDefToken.decs) {
                    if (decorator.func instanceof Name) {
                        Name decoratorFuncName = (Name) decorator.func;
                        if (decoratorFuncName.id.equals("staticmethod")) {
                            decorationIcon = UIConstants.DECORATION_STATIC;
                        } else if (decoratorFuncName.id.equals("classmethod")) {
                            decorationIcon = UIConstants.DECORATION_CLASS;
                        }
                    }
                }
            }
            if (qualifierIcon != null) {
                //it's OK if the decorationIcon is null as that's properly handled in getImageDecorated.
                return imageCache.getImageDecorated(UIConstants.METHOD_ICON, qualifierIcon,
                        ImageCache.DECORATION_LOCATION_BOTTOM_RIGHT, decorationIcon,
                        ImageCache.DECORATION_LOCATION_TOP_RIGHT);

            } else if (decorationIcon != null) {
                return imageCache.getImageDecorated(UIConstants.METHOD_ICON, decorationIcon,
                        ImageCache.DECORATION_LOCATION_TOP_RIGHT);
            }

            return imageCache.get(UIConstants.METHOD_ICON);

        } else if (token instanceof Import) {
            return imageCache.get(UIConstants.IMPORT_ICON);
        } else if (token instanceof If && NodeUtils.isIfMAinNode((If) token)) {
            return imageCache.get(UIConstants.MAIN_FUNCTION_ICON);
        } else if (token instanceof ImportFrom) {
            return imageCache.get(UIConstants.IMPORT_ICON);
        } else if (token instanceof commentType) {
            return imageCache.get(UIConstants.COMMENT);
        } else if (token instanceof Attribute || token instanceof Name || token instanceof NameTok) {
            String name = null;
            if (token instanceof Attribute) {
                Attribute attributeToken = (Attribute) token;
                name = NodeUtils.getNameFromNameTok((NameTok) (attributeToken).attr);
            } else if (token instanceof Name) {
                Name nameToken = (Name) token;
                name = nameToken.id;
            } else {
                NameTok nameTokToken = (NameTok) token;
                name = NodeUtils.getNameFromNameTok(nameTokToken);
            }

            String image;
            if (name.startsWith("__")) {
                if (name.endsWith("__")) {
                    image = UIConstants.PUBLIC_ATTR_ICON;
                } else {
                    image = UIConstants.PRIVATE_FIELD_ICON;
                }
            } else if (name.startsWith("_")) {
                image = UIConstants.PROTECTED_FIELD_ICON;
            } else {
                image = UIConstants.PUBLIC_ATTR_ICON;
            }

            if (parent != null && parent.node != null && parent.node instanceof ClassDef) {
                return imageCache.getImageDecorated(image, UIConstants.DECORATION_CLASS);
            }
            return imageCache.get(image);

        } else {
            return imageCache.get(UIConstants.ERROR);
        }
    }

    public ParsedItem[] getChildren() {
        if (children != null) {
            return children;
        }
        if (astChildrenEntries == null) {
            astChildrenEntries = new ASTEntryWithChildren[0];
        }

        ArrayList<ParsedItem> items = new ArrayList<ParsedItem>();

        //only the root can have an error as a child (from there on, the errors don't contain inner errors)
        if (this.parent == null && errorDesc != null && errorDesc.message != null) {
            items.add(new ParsedItem(this, errorDesc));
        }

        for (ASTEntryWithChildren c : astChildrenEntries) {
            items.add(new ParsedItem(this, c, c.getChildren()));
        }
        children = items.toArray(new ParsedItem[items.size()]);
        return children;
    }

    public ParsedItem getParent() {
        return parent;
    }

    /**
     * When null, it must be rebuilt!
     */
    private String toStringCache;

    public String toString() {
        if (toStringCache == null) {
            toStringCache = calcToString();
        }
        return toStringCache;
    }

    private String calcToString() {
        if (errorDesc != null && errorDesc.message != null) {
            return errorDesc.message;
        }

        if (astThis == null) {
            return "null";

        } else if (astThis.node instanceof If && NodeUtils.isIfMAinNode((If) astThis.node)) {
            return "__main__";

        } else if (astThis.node instanceof Import) {
            aliasType[] imports = ((Import) astThis.node).names;
            FastStringBuffer retVal = new FastStringBuffer();
            for (int i = 0; i < imports.length; i++) {
                aliasType aliasType = imports[i];

                //as ...
                if (aliasType.asname != null) {
                    retVal.append(((NameTok) aliasType.asname).id);
                    retVal.append(" = ");
                }

                retVal.append(((NameTok) aliasType.name).id);
                retVal.append(", ");
            }
            //delete the last 2 chars
            retVal.deleteLast();
            retVal.deleteLast();
            return retVal.toString();

        } else if (astThis.node instanceof ImportFrom) {
            // from wxPython.wx import *
            ImportFrom importToken = (ImportFrom) astThis.node;
            StringBuffer modules = new StringBuffer();
            for (int i = 0; i < importToken.names.length; i++) {
                aliasType aliasType = importToken.names[i];

                //as ...
                if (aliasType.asname != null) {
                    modules.append(((NameTok) aliasType.asname).id);
                    modules.append(" = ");
                }

                modules.append(((NameTok) aliasType.name).id);
                modules.append(",");
            }
            if (modules.length() == 0) {
                modules.append("*,"); //the comma will be deleted
            }
            modules.deleteCharAt(modules.length() - 1);
            return modules.toString() + " (" + ((NameTok) importToken.module).id + ")";

        } else if (astThis.node instanceof commentType) {
            commentType type = (commentType) astThis.node;
            String rep = type.id.trim();
            rep = StringUtils.split(rep, '\n').get(0);
            rep = StringUtils.split(rep, '\r').get(0);
            rep = rep.substring(1);
            rep = StringUtils.rightTrim(rep, '-');
            return StringUtils.leftTrim(rep, '-');

        } else {
            return NodeUtils.getFullRepresentationString(astThis.node);
        }
    }

    /**
     * @return rank for sorting ParserItems. When comparing
     * two items, first we compare class ranking, then titles
     */
    public int getClassRanking() {
        int rank;

        if (astThis == null || (errorDesc != null && errorDesc.message != null)) {
            rank = -2;
        } else if (astThis.node instanceof ImportFrom) {
            rank = 0;
        } else if (astThis.node instanceof Import) {
            rank = 1;
        } else if (astThis.node instanceof commentType) {
            rank = -1;
        } else {
            rank = 10;
        }
        return rank;
    }

    public int compareTo(Object o) {
        if (!(o instanceof ParsedItem)) {
            return 0;
        }
        ParsedItem item = (ParsedItem) o;
        int myRank = getClassRanking();
        int rank = item.getClassRanking();

        if (myRank == rank) {
            if (rank == -1) {
                return astThis.node.beginLine < item.astThis.node.beginLine ? -1 : 1;
            } else {
                return toString().compareTo(item.toString());
            }

        } else {
            return (myRank < rank ? -1 : 1);
        }
    }

    /**
     * Updates the structure of this parsed item (old structure) to be the same as the structure in the passed
     * parsed item (new structure) trying to reuse the existing children (if possible).
     * 
     * This is usually only called when the structure actually changes (different number of nodes). A common case
     * is having a syntax error...
     */
    public void updateTo(ParsedItem updateToItem) {
        this.toStringCache = null;
        this.errorDesc = updateToItem.errorDesc;
        this.astThis = updateToItem.astThis;
        this.astChildrenEntries = updateToItem.astChildrenEntries;

        ParsedItem[] newStructureChildren = updateToItem.getChildren();

        //handle special cases...
        if (this.children == null) {
            this.children = newStructureChildren;
            return;
        }

        if (newStructureChildren.length == 0 || this.children.length == 0) {
            //nothing to actually update... (just set the new children directly)
            this.children = newStructureChildren;
            return;
        }

        ArrayList<ParsedItem> newChildren = new ArrayList<ParsedItem>();

        //ok, something there... let's update the requested children... 
        //(trying to maintain the existing nodes were possible)
        HashMap<String, List<ParsedItem>> childrensCache = new HashMap<String, List<ParsedItem>>();
        for (ParsedItem existing : this.children) {
            String s = existing.toString();
            List<ParsedItem> list = childrensCache.get(s);
            if (list == null) {
                list = new ArrayList<ParsedItem>();
                childrensCache.put(s, list);
            }
            list.add(existing);
        }

        for (ParsedItem n : newStructureChildren) {
            ParsedItem similarChild = getSimilarChild(n, childrensCache);
            if (similarChild != null) {
                similarChild.updateTo(n);
                n = similarChild;
            } else {
                n.parent = this;
            }
            newChildren.add(n);
        }

        this.children = newChildren.toArray(new ParsedItem[newChildren.size()]);
    }

    private ParsedItem getSimilarChild(ParsedItem n, HashMap<String, List<ParsedItem>> childrensCache) {
        //try to get a similar child from the 'cache'
        List<ParsedItem> list = childrensCache.get(n.toString());
        if (list != null && list.size() > 0) {
            return list.remove(0);
        }
        return null;
    }

}

/*
 * License: Common Public License v1.0
 * Created on Sep 12, 2005
 * 
 * @author Fabio Zadrozny
 */
package com.python.pydev.analysis.additionalinfo;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.python.pydev.core.ICompletionState;
import org.python.pydev.core.IToken;
import org.python.pydev.core.ModulesKey;
import org.python.pydev.editor.codecompletion.CompletionRequest;
import org.python.pydev.editor.codecompletion.IPyDevCompletionParticipant;
import org.python.pydev.editor.codecompletion.PyCodeCompletionUtils;
import org.python.pydev.editor.codecompletion.revisited.CompletionStateFactory;
import org.python.pydev.editor.codecompletion.revisited.ModulesManager;
import org.python.pydev.editor.codecompletion.revisited.modules.AbstractModule;
import org.python.pydev.editor.codecompletion.revisited.modules.SourceModule;
import org.python.pydev.parser.jython.SimpleNode;
import org.python.pydev.plugin.nature.PythonNature;

import com.python.pydev.analysis.AnalysisTestsBase;
import com.python.pydev.analysis.builder.AnalysisRunner;
import com.python.pydev.analysis.ctrl_1.MarkerStub;

public class AdditionalInfoTestsBase extends AnalysisTestsBase {

    protected IPyDevCompletionParticipant participant;
    protected boolean useOriginalRequestCompl = false;
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    protected ArrayList<IToken> imports;
    
    public ICompletionProposal[] requestCompl(File file, String strDoc, int documentOffset, int returned, String []retCompl, PythonNature nature) throws CoreException, BadLocationException{
        if(useOriginalRequestCompl){
            return super.requestCompl(file, strDoc, documentOffset, returned, retCompl, nature);
        }
        
        if(documentOffset == -1){
            documentOffset = strDoc.length();
        }
        
        IDocument doc = new Document(strDoc);
        CompletionRequest request = new CompletionRequest(file, nature, doc, documentOffset, codeCompletion);

        ICompletionState state = CompletionStateFactory.getEmptyCompletionState(nature);
        state.setTokenImportedModules(imports);
        List<Object> props = new ArrayList<Object>(participant.getGlobalCompletions(request, state));
        ICompletionProposal[] codeCompletionProposals = PyCodeCompletionUtils.onlyValidSorted(props, request.qualifier, request.isInCalltip);
        
        
        for (int i = 0; i < retCompl.length; i++) {
            assertContains(retCompl[i], codeCompletionProposals);
        }

        if(returned > -1){
            StringBuffer buffer = getAvailableAsStr(codeCompletionProposals);
            assertEquals("Expected "+returned+" received: "+codeCompletionProposals.length+"\n"+buffer, returned, codeCompletionProposals.length);
        }
        return codeCompletionProposals;
    }

    /**
     * This method creates a marker stub
     * 
     * @param start start char
     * @param end end char
     * @param type the marker type
     * @return the created stub
     */
    @SuppressWarnings("unchecked")
	protected MarkerStub createMarkerStub(int start, int end, int type) {
        HashMap attrs = new HashMap();

        attrs.put(AnalysisRunner.PYDEV_ANALYSIS_TYPE, type);
        attrs.put(IMarker.CHAR_START, start);
        attrs.put(IMarker.CHAR_END, end);
    
        MarkerStub marker = new MarkerStub(attrs);
        return marker;
    }
    
	protected void addFooModule(final SimpleNode ast) {
	    String modName = "foo";
	    PythonNature natureToAdd = nature;
		addModuleToNature(ast, modName, natureToAdd);
	}

    /**
     * @param ast the ast that defines the module
     * @param modName the module name
     * @param natureToAdd the nature where the module should be added
     */
    protected void addModuleToNature(final SimpleNode ast, String modName, PythonNature natureToAdd) {
        //this is to add the info from the module that we just created...
        AbstractAdditionalInterpreterInfo additionalInfo = AdditionalProjectInterpreterInfo.getAdditionalInfoForProject(natureToAdd.getProject());
        additionalInfo.addAstInfo(ast, modName, natureToAdd, false);
        ModulesManager modulesManager = (ModulesManager) natureToAdd.getAstManager().getModulesManager();
        SourceModule mod = (SourceModule) AbstractModule.createModule(ast, null, modName);
        modulesManager.doAddSingleModule(new ModulesKey(modName, null), mod);
    }
    
}

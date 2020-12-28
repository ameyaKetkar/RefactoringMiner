package gr.uom.java.xmi;

import gr.uom.java.xmi.diff.UMLClassDiff;
import gr.uom.java.xmi.diff.UMLModelDiff;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.refactoringminer.api.RefactoringMinerTimedOutException;

import static java.util.stream.Collectors.*;

public class UMLModel {
	private final Set<String> repositoryDirectories;
    private final List<UMLClass> classList;
    private final List<UMLGeneralization> generalizationList;
    private final List<UMLRealization> realizationList;

    public UMLModel(Set<String> repositoryDirectories, List<UMLClass> umlClasses, List<UMLGeneralization> generalizationList
			, List<UMLRealization> realizationList) {
    	this.repositoryDirectories = repositoryDirectories;
        this.classList = umlClasses;
		this.generalizationList = generalizationList;
		this.realizationList = realizationList;

    }

	public UMLModel(List<UMLClass> umlClasses) {
    	this(new HashSet<>(), umlClasses, new ArrayList<>(), new ArrayList<>());
	}

	public UMLModel(Set<String> repositoryDirectories) {
		this(repositoryDirectories, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
	}

    public static <T> List<T> merge(List<T> l1, List<T> l2){
    	return Stream.concat(l1.stream(), l2.stream()).distinct().collect(toList());
	}

	public static <T> Set<T> merge(Set<T> l1, Set<T> l2){
		return Stream.concat(l1.stream(), l2.stream()).collect(toSet());
	}

	public static UMLModel merge(UMLModel um1, UMLModel um2){
    	return new UMLModel(merge(um1.repositoryDirectories, um2.repositoryDirectories),
				merge(um1.classList, um2.classList), merge(um1.generalizationList, um2.generalizationList),
				merge(um1.realizationList, um2.realizationList));
	}

    public void addRealization(UMLRealization umlRealization) {
    	realizationList.add(umlRealization);
    }

    public UMLClass getClass(UMLClass umlClassFromOtherModel) {
    	ListIterator<UMLClass> it = classList.listIterator();
        while(it.hasNext()) {
            UMLClass umlClass = it.next();
            if(umlClass.equals(umlClassFromOtherModel))
                return umlClass;
        }
        return null;
    }

    public List<UMLClass> getClassList() {
        return this.classList;
    }

    public List<UMLGeneralization> getGeneralizationList() {
        return this.generalizationList;
    }

    public List<UMLRealization> getRealizationList() {
		return realizationList;
	}

	public UMLGeneralization matchGeneralization(UMLGeneralization otherGeneralization) {
    	ListIterator<UMLGeneralization> generalizationIt = generalizationList.listIterator();
    	while(generalizationIt.hasNext()) {
    		UMLGeneralization generalization = generalizationIt.next();
    		if(generalization.getChild().equals(otherGeneralization.getChild())) {
    			String thisParent = generalization.getParent();
    			String otherParent = otherGeneralization.getParent();
    			String thisParentComparedString = null;
    			if(thisParent.contains("."))
    				thisParentComparedString = thisParent.substring(thisParent.lastIndexOf(".")+1);
    			else
    				thisParentComparedString = thisParent;
    			String otherParentComparedString = null;
    			if(otherParent.contains("."))
    				otherParentComparedString = otherParent.substring(otherParent.lastIndexOf(".")+1);
    			else
    				otherParentComparedString = otherParent;
    			if(thisParentComparedString.equals(otherParentComparedString))
    				return generalization;
    		}
    	}
    	return null;
    }

    public UMLRealization matchRealization(UMLRealization otherRealization) {
    	ListIterator<UMLRealization> realizationIt = realizationList.listIterator();
    	while(realizationIt.hasNext()) {
    		UMLRealization realization = realizationIt.next();
    		if(realization.getClient().equals(otherRealization.getClient())) {
    			String thisSupplier = realization.getSupplier();
    			String otherSupplier = otherRealization.getSupplier();
    			String thisSupplierComparedString = null;
    			if(thisSupplier.contains("."))
    				thisSupplierComparedString = thisSupplier.substring(thisSupplier.lastIndexOf(".")+1);
    			else
    				thisSupplierComparedString = thisSupplier;
    			String otherSupplierComparedString = null;
    			if(otherSupplier.contains("."))
    				otherSupplierComparedString = otherSupplier.substring(otherSupplier.lastIndexOf(".")+1);
    			else
    				otherSupplierComparedString = otherSupplier;
    			if(thisSupplierComparedString.equals(otherSupplierComparedString))
    				return realization;
    		}
    	}
    	return null;
    }

    public UMLModelDiff diff(UMLModel umlModel) throws RefactoringMinerTimedOutException {
    	return this.diff(umlModel, Collections.<String, String>emptyMap());
    }

	public UMLModelDiff diff(UMLModel umlModel, Map<String, String> renamedFileHints) throws RefactoringMinerTimedOutException {
    	UMLModelDiff modelDiff = new UMLModelDiff();
    	for(UMLClass umlClass : classList) {
    		if(!umlModel.classList.contains(umlClass))
    			modelDiff.reportRemovedClass(umlClass);
    	}
    	for(UMLClass umlClass : umlModel.classList) {
    		if(!this.classList.contains(umlClass))
    			modelDiff.reportAddedClass(umlClass);
    	}
    	modelDiff.checkForMovedClasses(renamedFileHints, umlModel.repositoryDirectories, new UMLClassMatcher.Move());
    	modelDiff.checkForRenamedClasses(renamedFileHints, new UMLClassMatcher.Rename());
    	for(UMLGeneralization umlGeneralization : generalizationList) {
    		if(!umlModel.generalizationList.contains(umlGeneralization))
    			modelDiff.reportRemovedGeneralization(umlGeneralization);
    	}
    	for(UMLGeneralization umlGeneralization : umlModel.generalizationList) {
    		if(!this.generalizationList.contains(umlGeneralization))
    			modelDiff.reportAddedGeneralization(umlGeneralization);
    	}
    	modelDiff.checkForGeneralizationChanges();
    	for(UMLRealization umlRealization : realizationList) {
    		if(!umlModel.realizationList.contains(umlRealization))
    			modelDiff.reportRemovedRealization(umlRealization);
    	}
    	for(UMLRealization umlRealization : umlModel.realizationList) {
    		if(!this.realizationList.contains(umlRealization))
    			modelDiff.reportAddedRealization(umlRealization);
    	}
    	modelDiff.checkForRealizationChanges();
    	for(UMLClass umlClass : classList) {
    		if(umlModel.classList.contains(umlClass)) {
    			UMLClassDiff classDiff = new UMLClassDiff(umlClass, umlModel.getClass(umlClass), modelDiff);
    			classDiff.process();
    			if(!classDiff.isEmpty())
    				modelDiff.addUMLClassDiff(classDiff);
    		}
    	}
    	modelDiff.checkForMovedClasses(renamedFileHints, umlModel.repositoryDirectories, new UMLClassMatcher.RelaxedMove());
    	modelDiff.checkForRenamedClasses(renamedFileHints, new UMLClassMatcher.RelaxedRename());
    	return modelDiff;
    }
}

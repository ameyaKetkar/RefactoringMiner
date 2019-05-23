package gr.uom.java.xmi.diff;

import static java.util.stream.Collectors.toList;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import gr.uom.java.xmi.UMLClass;

public class ClassDiff<U,V> implements Refactoring {

    protected UMLClass b4;
    protected UMLClass aftr;

    ClassDiff(UMLClass b4, UMLClass aftr){
        this.b4 = b4;
        this.aftr = aftr;
    }

    @Override
    public RefactoringType getRefactoringType() {
        return RefactoringType.CLASS_DIFF_PROVIDER;
    }

    @Override
    public String getName() {
        return RefactoringType.CLASS_DIFF_PROVIDER.getDisplayName();
    }

    @Override
    public List<String> getInvolvedClassesBeforeRefactoring() {
        return Stream.of(b4.getName()).collect(toList());
    }

    @Override
    public List<String> getInvolvedClassesAfterRefactoring() {
        return Stream.of(aftr.getName()).collect(toList());
    }

    @Override
    public List<CodeRange> leftSide() {
        return new ArrayList<>();
    }

    @Override
    public List<CodeRange> rightSide() {
        return new ArrayList<>();
    }

    public ImmutablePair<UMLClass,UMLClass> getClassDiff(){
        return new ImmutablePair<>(b4,aftr);
    }

    public UMLClass getClsB4(){
        return b4;
    }

    public UMLClass getClsAftr(){
        return aftr;
    }
}

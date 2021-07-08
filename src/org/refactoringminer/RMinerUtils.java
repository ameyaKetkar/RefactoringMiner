package org.refactoringminer;

import com.google.gson.Gson;
import gr.uom.java.xmi.LocationInfo;
import gr.uom.java.xmi.decomposition.AbstractCodeMapping;
import gr.uom.java.xmi.decomposition.LeafMapping;
import gr.uom.java.xmi.diff.*;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.refactoringminer.api.Refactoring;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

public class RMinerUtils {

        public static class Response {
            public List<CommitData> commits;
        }

        public static class CommitData {
            public String repository;
            public String sha1;
            public String url;
            public List<TypeChange> refactorings;

        }

        public static class TypeChange{
            private final String beforeName;
            private final String afterName;
            private ImmutablePair<String, String> beforeCu;
            private ImmutablePair<String, String> afterCu;
            private String b4Type;
            private String afterType;
            private LocationInfo locationInfoBefore;
            private LocationInfo locationInfoAfter;
            private List<String> addedImportStatements;
            private List<String> removedImportStatements;
            private List<String> unchangedImportStatements;
            private final String RefactoringKind;
            private List<Statement_Mapping> references;

            public TypeChange(String beforeName, String afterName, String refactoringKind){
                this.beforeName = beforeName;
                this.afterName = afterName;
                RefactoringKind = refactoringKind;
            }

            public TypeChange(String beforeName, String afterName, ImmutablePair<String, String> beforeCu, ImmutablePair<String, String> afterCu, String b4Type,
                              String afterType, LocationInfo locationInfoBefore, LocationInfo locationInfoAfter, Statement_Mapping varDeclLoc, List<Statement_Mapping> references,
                              List<String> addedImportStatements, List<String> removedImportStatements, List<String> unchangedImportStatements, String refactoringKind) {
                this.beforeName = beforeName;
                this.afterName = afterName;
                this.beforeCu = beforeCu;
                this.afterCu = afterCu;
                this.b4Type = b4Type;
                this.afterType = afterType;
                this.locationInfoBefore = locationInfoBefore;
                this.locationInfoAfter = locationInfoAfter;
                this.references = references;
                this.addedImportStatements = addedImportStatements;
                this.removedImportStatements = removedImportStatements;
                this.unchangedImportStatements = unchangedImportStatements;
                RefactoringKind = refactoringKind;
                if(varDeclLoc != null){
                    if(this.references.isEmpty()) {
                        this.references = List.of(varDeclLoc);
                    }else{
                        this.references.add(varDeclLoc);
                    }
                }
            }

            public String getBeforeName() {
                return beforeName;
            }

            public String getAfterName() {
                return afterName;
            }

            public ImmutablePair<String, String> getBeforeCu() {
                return beforeCu;
            }

            public ImmutablePair<String, String> getAfterCu() {
                return afterCu;
            }

            public String getB4Type() {
                return b4Type;
            }

            public String getAfterType() {
                return afterType;
            }


            public List<Statement_Mapping> getReferences() {
                return references;
            }

            public LocationInfo getLocationInfoBefore() {
                return locationInfoBefore;
            }

            public LocationInfo getLocationInfoAfter() {
                return locationInfoAfter;
            }

            public List<String> getAddedImportStatements() {
                return addedImportStatements;
            }

            public List<String> getRemovedImportStatements() {
                return removedImportStatements;
            }

            public void setRemovedImportStatements(List<String> removedImportStatements) {
                this.removedImportStatements = removedImportStatements;
            }

            public List<String> getUnchangedImportStatements() {
                return unchangedImportStatements;
            }

            public String getRefactoringKind() {
                return RefactoringKind;
            }
        }

        public static class Statement_Mapping {
            private final String beforeStmt;
            private final String afterStmt;
            private final LocationInfo locationInfoBefore;
            private final LocationInfo locationInfoAfter;
            private final List<String> replacements;
            private final boolean isSimilar;

            public Statement_Mapping(String beforeStmt, String afterStmt, LocationInfo locationInfoBefore, LocationInfo locationInfoAfter, List<String> replacements, boolean isSimilar) {
                this.beforeStmt = beforeStmt;
                this.afterStmt = afterStmt;
                this.locationInfoBefore = locationInfoBefore;
                this.locationInfoAfter = locationInfoAfter;
                this.replacements = replacements;
                this.isSimilar = isSimilar;
            }

            public String getBeforeStmt() {
                return beforeStmt;
            }

            public String getAfterStmt() {
                return afterStmt;
            }

            public List<String> getReplacements() {
                return replacements;
            }

            public LocationInfo getLocationInfoBefore() {
                return locationInfoBefore;
            }

            public LocationInfo getLocationInfoAfter() {
                return locationInfoAfter;
            }

            public boolean isSimilar() {
                return isSimilar;
            }
        }

    public static List<Statement_Mapping> toStmtMapping(Collection<AbstractCodeMapping> acm){
        return acm.stream().map(RMinerUtils::toStmtMapping).collect(Collectors.toList());
    }


    public static Statement_Mapping toStmtMapping(AbstractCodeMapping acm){
            return new Statement_Mapping(acm.getFragment1().getString(), acm.getFragment2().getString(),
                    acm.getFragment1().getLocationInfo(),acm.getFragment2().getLocationInfo(),
                    acm.getReplacements().stream().map(x->x.getType().toString()).collect(Collectors.toList()),
                    acm.isExact() || acm.isIdenticalWithExtractedVariable() || acm.isIdenticalWithInlinedVariable());
    }

    public static Statement_Mapping toStmtMapping(ChangeVariableTypeRefactoring cvt){
        if (cvt.getOriginalVariable().getInitializer() == null || cvt.getChangedTypeVariable().getInitializer() == null
                || cvt.getOriginalVariable().getInitializer().equalFragment(cvt.getChangedTypeVariable().getInitializer()))
            return null;
        AbstractCodeMapping acm = new LeafMapping(cvt.getOriginalVariable().getInitializer(), cvt.getChangedTypeVariable().getInitializer(),
                cvt.getOperationBefore(), cvt.getOperationAfter());

        return new Statement_Mapping(cvt.getOriginalVariable().getVariableName() + " = " + acm.getFragment1().getString(),
                cvt.getChangedTypeVariable().getVariableName() + " = " + acm.getFragment2().getString(),
                acm.getFragment1().getLocationInfo(),acm.getFragment2().getLocationInfo(),
                acm.getReplacements().stream().map(x->x.getType().toString()).collect(Collectors.toList()),
                acm.isExact() || acm.isIdenticalWithExtractedVariable() || acm.isIdenticalWithInlinedVariable());
    }

    public static <T> List<T> difference(final List<T> setOne, final List<T> setTwo) {
        Set<T> result = new HashSet<T>(setOne);
        result.removeIf(setTwo::contains);
        return new ArrayList<>(result);
    }

    public static <T> List<T> intersection(final List<T> setOne, final List<T> setTwo) {
        Set<T> result = new HashSet<>(setOne);
        result.removeIf(x -> !setTwo.contains(x));
        return new ArrayList<>(result);
    }

    public static String getJsonForRelevant(Refactoring r) {
        String json = "";
        String refactoringKind = r.getRefactoringType().toString();
        if(r instanceof ChangeReturnTypeRefactoring){
            ChangeReturnTypeRefactoring crt = (ChangeReturnTypeRefactoring) r;
            var tc = new TypeChange(crt.getOperationBefore().getName(), crt.getOperationAfter().getName()
                ,crt.getInvolvedClassesBeforeRefactoring().iterator().next()
                ,crt.getInvolvedClassesAfterRefactoring().iterator().next()
                ,crt.getOriginalType().toQualifiedString(), crt.getChangedType().toQualifiedString()
                ,crt.getOperationBefore().getLocationInfo(), crt.getOperationAfter().getLocationInfo(), null,
                    toStmtMapping(crt.getReturnReferences()),
                    difference(crt.getOperationAfter().getImportedTypes(), crt.getOperationBefore().getImportedTypes()),
                    difference(crt.getOperationBefore().getImportedTypes(), crt.getOperationAfter().getImportedTypes()),
                    intersection(crt.getOperationAfter().getImportedTypes(), crt.getOperationBefore().getImportedTypes()), refactoringKind);

            json = new Gson().toJson(tc, TypeChange.class);
        }
        if(r instanceof ChangeAttributeTypeRefactoring){
            ChangeAttributeTypeRefactoring cat = (ChangeAttributeTypeRefactoring) r;
            var tc = new TypeChange(cat.getOriginalAttribute().getName(), cat.getChangedTypeAttribute().getName()
                    ,cat.getInvolvedClassesBeforeRefactoring().iterator().next()
                    ,cat.getInvolvedClassesAfterRefactoring().iterator().next()
                    ,cat.getOriginalAttribute().getType().toQualifiedString(), cat.getChangedTypeAttribute().getType().toQualifiedString()
                    , cat.getOriginalAttribute().getLocationInfo(), cat.getChangedTypeAttribute().getLocationInfo(), null, toStmtMapping(cat.getAttributeReferences()),
                    difference(cat.getChangedTypeAttribute().getImportedTypes(),cat.getOriginalAttribute().getImportedTypes()),
                    difference(cat.getOriginalAttribute().getImportedTypes(),cat.getChangedTypeAttribute().getImportedTypes()),
                    intersection(cat.getOriginalAttribute().getImportedTypes(),cat.getChangedTypeAttribute().getImportedTypes()), refactoringKind);

            json = new Gson().toJson(tc, TypeChange.class);
        }
        if(r instanceof ChangeVariableTypeRefactoring){
            ChangeVariableTypeRefactoring cvt = (ChangeVariableTypeRefactoring) r;

            var tc = new TypeChange(cvt.getOriginalVariable().getVariableName(), cvt.getChangedTypeVariable().getVariableName()
                    ,cvt.getInvolvedClassesBeforeRefactoring().iterator().next()
                    ,cvt.getInvolvedClassesAfterRefactoring().iterator().next()
                    ,cvt.getOriginalVariable().getType().toQualifiedString(), cvt.getChangedTypeVariable().getType().toQualifiedString()
                    , cvt.getOriginalVariable().getLocationInfo(), cvt.getChangedTypeVariable().getLocationInfo(), toStmtMapping(cvt),
                    toStmtMapping(cvt.getVariableReferences()),
                    difference(cvt.getOperationAfter().getImportedTypes(), cvt.getOperationBefore().getImportedTypes()),
                    difference(cvt.getOperationBefore().getImportedTypes(), cvt.getOperationAfter().getImportedTypes()),
                    intersection(cvt.getOperationBefore().getImportedTypes(), cvt.getOperationAfter().getImportedTypes()), refactoringKind);

            json = new Gson().toJson(tc, TypeChange.class);
        }
        if(r instanceof RenameAttributeRefactoring){
            RenameAttributeRefactoring rar = (RenameAttributeRefactoring) r;
            var re = new TypeChange(rar.getOriginalAttribute().getName(), rar.getRenamedAttribute().getName(), refactoringKind);
            json = new Gson().toJson(re, TypeChange.class);
        }
        if(r instanceof RenameVariableRefactoring){
            RenameVariableRefactoring rar = (RenameVariableRefactoring) r;
            var re = new TypeChange(rar.getOriginalVariable().getVariableName(), rar.getRenamedVariable().getVariableName(), refactoringKind);
            json = new Gson().toJson(re, TypeChange.class);
        }
        if(r instanceof RenameOperationRefactoring){
            RenameOperationRefactoring rar = (RenameOperationRefactoring) r;
            var re = new TypeChange(rar.getOriginalOperation().getName(), rar.getRenamedOperation().getName(), refactoringKind);
            json = new Gson().toJson(re, TypeChange.class);
        }
        if(r instanceof RenameClassRefactoring){
            RenameClassRefactoring rar = (RenameClassRefactoring) r;
            var re = new TypeChange(rar.getOriginalClassName(),  rar.getRenamedClassName(), refactoringKind);
            json = new Gson().toJson(re, TypeChange.class);
        }
        return json;
    }
}




package Utilities;

import com.google.gson.Gson;
import gr.uom.java.xmi.LocationInfo;
import gr.uom.java.xmi.decomposition.AbstractCodeMapping;
import gr.uom.java.xmi.diff.*;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.eclipse.jgit.lib.Repository;
import org.refactoringminer.api.GitHistoryRefactoringMiner;
import org.refactoringminer.api.GitService;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;
import org.refactoringminer.util.GitServiceImpl;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class RMinerUtils {

        public static Path getProjectFolder(String project){
            return Paths.get("/Users/ameya/Research/TypeChangeStudy/Corpus").resolve("Project_"+project).resolve(project);
        }



        public static class Rename {
            private String beforeName;
            private String afterName;

            public Rename(String beforeName, String afterName) {
                this.beforeName = beforeName;
                this.afterName = afterName;
            }

            public String getBeforeName() {
                return beforeName;
            }

            public String getAfterName() {
                return afterName;
            }

            public void setAfterName(String afterName) {
                this.afterName = afterName;
            }
        }


        public class ExtractInlineVariable {
            private List<Statement_Mapping> references;

            public List<Statement_Mapping> getReferences() {
                return references;
            }

            public ExtractInlineVariable(List<Statement_Mapping> references) {
                this.references = references;
            }

        }

        public static class TypeChange{
            private String beforeName;
            private String afterName;
            private ImmutablePair<String, String> beforeCu;
            private ImmutablePair<String, String> afterCu;
            private String b4Type;
            private String afterType;

            private List<Statement_Mapping> references;

            public TypeChange(String beforeName, String afterName, ImmutablePair<String, String> beforeCu, ImmutablePair<String, String> afterCu, String b4Type,
                              String afterType, Statement_Mapping varDeclLoc, List<Statement_Mapping> references) {
                this.beforeName = beforeName;
                this.afterName = afterName;
                this.beforeCu = beforeCu;
                this.afterCu = afterCu;
                this.b4Type = b4Type;
                this.afterType = afterType;
                this.references = references;
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



        }

        public static class Statement_Mapping {
            private String beforeStmt;
            private String afterStmt;
            private LocationInfo locationInfoBefore;
            private LocationInfo locationInfoAfter;
            private List<String> replacements;

            public Statement_Mapping(String beforeStmt, String afterStmt, LocationInfo locationInfoBefore, LocationInfo locationInfoAfter, List<String> replacements) {
                this.beforeStmt = beforeStmt;
                this.afterStmt = afterStmt;
                this.locationInfoBefore = locationInfoBefore;
                this.locationInfoAfter = locationInfoAfter;
                this.replacements = replacements;
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
        }

    public static List<Statement_Mapping> toStmtMapping(Collection<AbstractCodeMapping> acm){
        return acm.stream().map(x->toStmtMapping(x)).collect(Collectors.toList());
    }


    public static Statement_Mapping toStmtMapping(AbstractCodeMapping acm){
            return new Statement_Mapping(acm.getFragment1().getString(), acm.getFragment2().getString(), acm.getFragment1().getLocationInfo()
                    ,acm.getFragment2().getLocationInfo(), acm.getReplacements().stream().map(x->x.getType().toString()).collect(Collectors.toList()));
        }

        public static void getRelevantRefactorings(String project, String url, String commit) throws Exception {
            GitHistoryRefactoringMiner miner = new GitHistoryRefactoringMinerImpl();
            GitService gitService = new GitServiceImpl();
            Repository repo = gitService.cloneIfNotExists(getProjectFolder(project).toString(), url);

            miner.detectAtCommit(repo, commit, new RefactoringHandler() {
                @Override
                public void handle(String commitId, List<Refactoring> refactorings) {
                        for(var r: refactorings){
                            String json = "";
                            if(r instanceof ChangeReturnTypeRefactoring){
                                ChangeReturnTypeRefactoring crt = (ChangeReturnTypeRefactoring) r;
                                var tc = new TypeChange(crt.getOperationBefore().getName(), crt.getOperationAfter().getName()
                                    ,crt.getInvolvedClassesBeforeRefactoring().iterator().next()
                                    ,crt.getInvolvedClassesAfterRefactoring().iterator().next()
                                    ,crt.getOriginalType().toQualifiedString(), crt.getChangedType().toQualifiedString()
                                    , null, toStmtMapping(crt.getReturnReferences()));
                                json = new Gson().toJson(tc, TypeChange.class);
                            }
                            if(r instanceof ChangeAttributeTypeRefactoring){
                                ChangeAttributeTypeRefactoring crt = (ChangeAttributeTypeRefactoring) r;
                                var tc = new TypeChange(crt.getOriginalAttribute().getName(), crt.getChangedTypeAttribute().getName()
                                        ,crt.getInvolvedClassesBeforeRefactoring().iterator().next()
                                        ,crt.getInvolvedClassesAfterRefactoring().iterator().next()
                                        ,crt.getOriginalAttribute().getType().toQualifiedString(), crt.getChangedTypeAttribute().getType().toQualifiedString()
                                        , null, toStmtMapping(crt.getAttributeReferences()));

                                json = new Gson().toJson(tc, TypeChange.class);
                            }
                            if(r instanceof ChangeVariableTypeRefactoring){
                                ChangeVariableTypeRefactoring crt = (ChangeVariableTypeRefactoring) r;
                                var tc = new TypeChange(crt.getOriginalVariable().getVariableName(), crt.getChangedTypeVariable().getVariableName()
                                        ,crt.getInvolvedClassesBeforeRefactoring().iterator().next()
                                        ,crt.getInvolvedClassesAfterRefactoring().iterator().next()
                                        ,crt.getOriginalVariable().getType().toQualifiedString(), crt.getChangedTypeVariable().getType().toQualifiedString()
                                        , null, toStmtMapping(crt.getVariableReferences()));

                                json = new Gson().toJson(tc, TypeChange.class);
                            }
                            if(r instanceof RenameAttributeRefactoring){
                                RenameAttributeRefactoring rar = (RenameAttributeRefactoring) r;
                                var re = new Rename(rar.getOriginalAttribute().getName(), rar.getRenamedAttribute().getName());
                                json = new Gson().toJson(re, Rename.class);
                            }
                            if(r instanceof RenameVariableRefactoring){
                                RenameVariableRefactoring rar = (RenameVariableRefactoring) r;
                                var re = new Rename(rar.getOriginalVariable().getVariableName(), rar.getRenamedVariable().getVariableName());
                                json = new Gson().toJson(re, Rename.class);
                            }
                            if(r instanceof RenameOperationRefactoring){
                                RenameOperationRefactoring rar = (RenameOperationRefactoring) r;
                                var re = new Rename(rar.getOriginalOperation().getName(), rar.getRenamedOperation().getName());
                                json = new Gson().toJson(re, Rename.class);
                            }
                            System.out.println(json);
                        }
                }
            });
        }





    public static void main(String[] a) throws Exception{
            getRelevantRefactorings("neo4j", "https://github.com/neo4j/neo4j.git", "77a5e62f9d5a56a48f82b6bdd8519b18275bef1d");


    }

}




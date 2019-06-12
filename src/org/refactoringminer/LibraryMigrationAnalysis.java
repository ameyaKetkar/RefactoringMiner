package org.refactoringminer;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import com.google.protobuf.CodedInputStream;

import org.refactoringminer.Models.RMinedOuterClass.RMined;
import org.refactoringminer.Models.RMinedOuterClass.RMined.RefactoringMined;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class LibraryMigrationAnalysis {

    public static void main (String a[]){
        List<RMined> rMinedList = readRMined();

        rMinedList.stream()
                .flatMap(x->x.getTypeChangeAnalysisList().stream())
               // .peek(x-> System.out.println(pretty(x.getB4()) + " -> " + pretty(x.getAftr())))
                .collect(Collectors.toList());

        performRefactoringLevelAnalysis(rMinedList);

    }

    public static List<RMined> readRMined(){
        try {
            List<RMined> rmined = new ArrayList<>();
            String name =  "./src/Protos/LibraryMigration_RMined";
            String contents = new String(Files.readAllBytes(Paths.get(name + "BinSize.txt")));
            String[] x = contents.split(" ");
            List<Integer> y = Arrays.asList(x).stream().map(String::trim)
                    .filter(s -> !s.equals(""))
                    .map(Integer::parseInt).collect(Collectors.toList());
            InputStream is = new FileInputStream(name + "Bin.txt");
            for (Integer c : y) {
                byte[] b = new byte[c];
                int i = is.read(b);
                if (i > 0) {
                    CodedInputStream input = CodedInputStream.newInstance(b);
                    rmined.add(RMined.parseFrom(input));
                }
            }
            return rmined;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println( "LibraryMigrationRMined could not be deserialied");
            return new ArrayList<>();
        }
    }


    private static List<RMined> performRefactoringLevelAnalysis(List<RMined> rMinedList) {
        System.out.println("------------------- Refactoring Level analysis------------------");
        List<RMined> commitsWithRefactoring = rMinedList.stream().filter(x -> x.getRefactoringsCount() > 0).collect(toList());
        System.out.println("Total number of commits with Refactoring : " + commitsWithRefactoring.size());
        System.out.println(((double)commitsWithRefactoring.size()/ rMinedList.size()) + " commit contains refactoring");
        System.out.println("Top 15 refactorings in the corpus");
        List<Entry<String, Long>> refactoringDistribution = commitsWithRefactoring.stream()
                .flatMap(x -> x.getRefactoringsList().stream())
                .filter(x->!x.getRefactoringType().equals("CLASS_DIFF_PROVIDER"))
                .collect(groupingBy(x -> x.getRefactoringType(), Collectors.counting()))
                .entrySet().stream().sorted(Comparator.comparingLong(e -> e.getValue()))
                .collect(toList());
        refactoringDistribution.stream()
                //.skip(refactoringDistribution.size() - 15)
                .forEach(e -> System.out.println(e.getKey() + " : " + e.getValue()));
        //TODO: Populate OtherRefactoringAnalysis field
   //     refactoringsUponChangeTypeElement(null);
        System.out.println("-----------------------");
        List<RMined> commitsWithTypeChanges = commitsWithRefactoring.stream()
                .filter(x->x.getRefactoringsList().stream().anyMatch(u->u.hasTypeChange())).collect(toList());
        System.out.println(((double)commitsWithTypeChanges.size()/ rMinedList.size()) + " commits contain type change refactoring");
        List<RefactoringMined> typeChangeRefactoring = commitsWithRefactoring.stream().flatMap(c -> c.getRefactoringsList().stream())
                .filter(x -> x.hasTypeChange()).collect(toList());
        System.out.println("Number of type change refactorings " + typeChangeRefactoring.size());

        System.out.println(((double)typeChangeRefactoring.size()/ rMinedList.size()) + " type change refactoring observed per commit");

        System.out.println("--------------------------XXXXXXXXXXXXX-----------------------------");
        return commitsWithTypeChanges;
    }

}

package org.refactoringminer;

import static org.refactoringminer.DetailedTypeAnalysisUtil.pretty;

import com.google.protobuf.CodedInputStream;

import org.refactoringminer.Models.RMinedOuterClass.RMined;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class LibraryMigrationAnalysis {

    public static void main (String a[]){
        List<RMined> rMinedList = readRMined();

        rMinedList.stream()
                .flatMap(x->x.getTypeChangeAnalysisList().stream())
                .forEach(x-> System.out.println(pretty(x.getB4()) + " -> " + pretty(x.getAftr())));




  //      System.out.println(x.size());
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
            //e.printStackTrace();
            System.out.println( "LibraryMigrationRMined could not be deserialied");
            return new ArrayList<>();
        }
    }
}

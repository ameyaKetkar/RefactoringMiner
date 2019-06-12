package org.refactoringminer;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.concat;
import static org.refactoringminer.DetailedTypeAnalysisUtil.DetailedTypeKind.*;

import org.refactoringminer.DetailedTypeAnalysisUtil.Match.Case;
import org.refactoringminer.Models.DetailedTypeOuterClass.DetailedType;
import org.refactoringminer.Models.DetailedTypeOuterClass.DetailedType.SimplType;
import org.refactoringminer.Models.DetailedTypeOuterClass.DetailedType.TypeOfType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;


public class DetailedTypeAnalysisUtil {

    enum DetailedTypeKind {
        SimpleType,
        ParameterizedType,
        WildcardType,
        UnionType,
        PrimitiveType,
        ArrayType,
        IntersectionType,
        QualifiedType,
    }


    public static DetailedTypeKind getDetailedTypeKind(DetailedType d){
            if(d.hasSimpleType()){
                return SimpleType;
            }else if(d.hasParamType()){
                return ParameterizedType;
            }else if(d.hasWildCrd()){
                return WildcardType;
            }else if(d.hasPrmtv()){
                return PrimitiveType;
            }else if(d.hasArryTyp()){
                return ArrayType;
            }else if(d.hasIntxnTyp()){
                return IntersectionType;
            }else if(d.hasUnionTyp()){
                return UnionType;
            }else if(d.hasQualTyp()){
                return QualifiedType;
            }
            return null;
    }


    public static <U> Match<U> Match(DetailedType dt){
        return new Match<>(dt);
    }

    public static class Match<U>{

        private final DetailedType dt;
        public Match(DetailedType dt){
            this.dt = dt;
        }

        public Optional<U> of(Case<U> ... c){
            if(dt == null)
                return Optional.empty();
            return Arrays.stream(c).filter(x->x.getKind().equals(getDetailedTypeKind(dt)))
                    .findFirst().map(f -> f.apply(dt));
        }

        public static class Case<U> {

            private final DetailedTypeKind dtk ;
            private final Function<DetailedType, U> func;

            public Case(DetailedTypeKind dtk, Function<DetailedType,U> func){
                this.dtk = dtk;
                this.func = func;
            }

            public DetailedTypeKind getKind(){
                return dtk;
            }

            public U apply(DetailedType dt){
                return func.apply(dt);
            }
    }

}

    public static String pretty(DetailedType dt){
        Optional<String> s = new Match<String>(dt).of(
                new Case<>(SimpleType
                        , t -> t.getSimpleType().getName() + String.join(" ", t.getSimpleType().getAnnotationsList()))
                , new Case<>(ParameterizedType
                        , t -> pretty(t.getParamType().getName()) + "<" + t.getParamType().getParamsList().stream().map(x -> pretty(x)).collect(joining(",")) + ">")
                , new Case<>(QualifiedType
                        , t -> t.getQualTyp().getName() + pretty(t.getQualTyp().getQualifier()) + String.join(" ", t.getQualTyp().getAnnotationsList()))
                , new Case<>(PrimitiveType
                        , t -> t.getPrmtv().getName() + String.join(" ", t.getPrmtv().getAnnotationsList()))
                , new Case<>(ArrayType
                        , t -> pretty(t.getArryTyp().getName()) + String.join(" ", t.getArryTyp().getAnnotationsList()) + "[]")
                , new Case<>(IntersectionType
                        , t -> t.getIntxnTyp().getTypesList().stream().map(x -> pretty(x)).collect(joining("&")))
                , new Case<>(UnionType
                        , t -> t.getUnionTyp().getTypesList().stream().map(x -> pretty(x)).collect(joining("|")))
                , new Case<>(WildcardType
                        , t -> t.getWildCrd().hasBound() ? "? " + t.getWildCrd().getSupOrext() + " " + pretty(t.getWildCrd().getBound()) + String.join(" ", t.getWildCrd().getAnnotationsList())
                        : "?" + String.join(" ", t.getWildCrd().getAnnotationsList())));
        return s.orElse("");
    }

    public static Set<DetailedType> getAllDetailedTypes (DetailedType dt){
        return new Match<Stream<DetailedType>>(dt).of(
                new Case<>(SimpleType, Stream::of)
                , new Case<>(PrimitiveType, Stream::of)
                , new Case<>(ArrayType, t -> concat(Stream.of(t), getAllDetailedTypes(t.getArryTyp().getName()).stream()))
                , new Case<>(ParameterizedType, t ->concat(getAllDetailedTypes(dt.getParamType().getName()).stream()
                        , dt.getParamType().getParamsList().stream().flatMap(x->getAllDetailedTypes(x).stream())))
                , new Case<>(UnionType, u -> concat(Stream.of(u), u.getUnionTyp().getTypesList().stream().flatMap(x->getAllDetailedTypes(x).stream())))
                , new Case<>(IntersectionType, u -> concat(Stream.of(u), u.getIntxnTyp().getTypesList().stream().flatMap(x->getAllDetailedTypes(x).stream())))
                , new Case<>(WildcardType, u -> concat(Stream.of(u), getAllDetailedTypes(u.getWildCrd().getBound()).stream()))
                , new Case<>(QualifiedType, q -> concat(Stream.of(q), getAllDetailedTypes(q.getQualTyp().getQualifier()).stream())))
                .map(s -> s.collect(toSet()))
                .orElse(new HashSet<>());
    }

    public static TypeOfType getTypeOfType(DetailedType dt){
        return new Match<TypeOfType>(dt).of(
                new Case<>(SimpleType, t -> t.getSimpleType().getTypeOfType())
                , new Case<>(PrimitiveType, t -> t.getPrmtv().getTypeOfType())
                , new Case<>(ArrayType, t -> getTypeOfType(t.getArryTyp().getName()))
                , new Case<>(ParameterizedType, t -> getTypeOfType(t.getParamType().getName()))
                , new Case<>(WildcardType, u -> u.getWildCrd().hasBound() ? getTypeOfType(u.getWildCrd().getBound()) : TypeOfType.WildCard)
                , new Case<>(QualifiedType, q -> getTypeOfType(q.getQualTyp().getQualifier()))).orElse(TypeOfType.DontKnow);
    }

    public static String getTopLevelName(DetailedType dt){
        return new Match<String>(dt).of(
                new Case<>(SimpleType, t -> t.getSimpleType().getName())
                , new Case<>(PrimitiveType, t -> t.getPrmtv().getName())
                , new Case<>(ArrayType, t -> getTopLevelName(t.getArryTyp().getName()))
                , new Case<>(ParameterizedType, t -> getTopLevelName(t.getParamType().getName()))
                , new Case<>(WildcardType, u -> getTopLevelName(u.getWildCrd().getBound()))
                , new Case<>(QualifiedType, q -> getTopLevelName(q.getQualTyp().getQualifier()))).orElse("");
    }

    public static List<SimplType> getSimpleTypes(DetailedType dt) {
        return getAllDetailedTypes(dt).stream().filter(x -> x.hasSimpleType())
                .map(x->x.getSimpleType())
                .collect(toList());
    }






}

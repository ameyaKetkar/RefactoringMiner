package gr.uom.java.xmi;

import static java.util.stream.Collectors.joining;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.IntersectionType;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.UnionType;
import org.eclipse.jdt.core.dom.WildcardType;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public abstract class DetailedType {

    public abstract TypeKind getTypeKind();
    public abstract String asStr();

    public static class SimpleTyp extends DetailedType{
        private String name;
        public SimpleTyp(String name) {
            this.name = name;
        }

        @Override
        public TypeKind getTypeKind() {
            return TypeKind.SimpleType;
        }

        public String asStr(){
            return name;
        }
    }

    public static class ParameterizedTyp extends DetailedType{
        private String name;
        private List<DetailedType> params;

        public ParameterizedTyp(String name, List<DetailedType> params) {
            this.name = name;
            this.params = params;
        }

        @Override
        public TypeKind getTypeKind() {
            return TypeKind.ParameterizedType;
        }

        @Override
        public String asStr() {
            return name + "<" + params.stream().map(d->d.asStr()).collect(joining(",")) + ">";
        }
    }

    public static class WildCardTyp extends DetailedType{
        private ImmutablePair<String,Optional<DetailedType>> t;

        public WildCardTyp(ImmutablePair<String, Optional<DetailedType>> t) {
            this.t = t;
        }


        @Override
        public TypeKind getTypeKind() {
            return TypeKind.WildcardType;
        }

        @Override
        public String asStr() {
            return "? " + t.getLeft() + " " + t.getRight();
        }
    }

    public static class PrimitiveTyp extends DetailedType{
        private String t;

        public PrimitiveTyp(String t) {
            this.t = t;
        }

        @Override
        public TypeKind getTypeKind() {
            return TypeKind.PrimitiveType;
        }

        @Override
        public String asStr() {
            return t;
        }
    }

    public static class ArrayTyp extends DetailedType{
        private DetailedType t;
        private int dim;

        public ArrayTyp(DetailedType t, int dim) {
            this.t = t;
            this.dim = dim;
        }

        @Override
        public TypeKind getTypeKind() {
            return TypeKind.ArrayType;
        }

        @Override
        public String asStr() {
            return t + IntStream.range(0, dim).mapToObj(i -> "[]").collect(joining(","));
        }
    }

    public static class InterSectionTyp extends DetailedType{
        private List<DetailedType> t;

        public InterSectionTyp(List<DetailedType> t) {
            this.t = t;
        }

        @Override
        public TypeKind getTypeKind() {
            return TypeKind.IntersectionType;
        }

        @Override
        public String asStr() {
            return t.stream().map(DetailedType::asStr).collect(joining("&"));
        }
    }

    public static class UnionTyp extends DetailedType{
        private List<DetailedType> t;

        public UnionTyp(List<DetailedType> t) {
            this.t = t;
        }

        @Override
        public TypeKind getTypeKind() {
            return TypeKind.UnionType;
        }

        @Override
        public String asStr() {
            return t.stream().map(DetailedType::asStr).collect(joining("|"));
        }
    }

    enum TypeKind{
        SimpleType,
        ParameterizedType,
        UnionType,
        IntersectionType,
        PrimitiveType,
        ArrayType,
        WildcardType;

    }

    public static DetailedType getDetailedType(Type t) {
        if(t.isSimpleType()){
            SimpleType st = (SimpleType) t;
            return new SimpleTyp(st.getName().getFullyQualifiedName());
        }
        else if(t.isParameterizedType()){
            ParameterizedType pt = (ParameterizedType) t;
            List<Type> params = pt.typeArguments();
            return new ParameterizedTyp(pt.getType().toString(),params.stream().map(x-> getDetailedType(x)).collect(Collectors.toList()));
        }
        else if(t.isWildcardType()){
            WildcardType wt = (WildcardType) t;
            return new WildCardTyp(ImmutablePair.of(wt.isUpperBound()?"super":"extends", Optional.ofNullable(wt.getBound()).map(DetailedType::getDetailedType)));
        }
        else if(t.isPrimitiveType()){
            PrimitiveType pt = (PrimitiveType) t;
            return new PrimitiveTyp(pt.getPrimitiveTypeCode().toString());
        }
        else if(t.isArrayType()){
            ArrayType at = (ArrayType) t;
            return new ArrayTyp(getDetailedType(at.getElementType()),at.dimensions().size());
        }
        else if(t.isIntersectionType()){
            IntersectionType it = (IntersectionType) t;
            List<Type> ts = it.types();
            return new InterSectionTyp(ts.stream().map(z->getDetailedType(z)).collect(Collectors.toList()));
        }
        else if(t.isUnionType()){
            UnionType ut = (UnionType) t;
            List<Type> ts = ut.types();
            return new UnionTyp(ts.stream().map(z->getDetailedType(z)).collect(Collectors.toList()));
        }else {
            return new SimpleTyp(t.toString());
        }
    }


}

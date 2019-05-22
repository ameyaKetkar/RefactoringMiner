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

        public String getName(){
            return this.name;
        }
    }

    public static class ParameterizedTyp extends DetailedType{


        private String name;
        private List<DetailedType> params;

        public ParameterizedTyp(String name, List<DetailedType> params) {
            this.name = name;
            this.params = params;
        }

        public String getName() {
            return name;
        }

        public List<DetailedType> getParams() {
            return params;
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
        private ImmutablePair<String,Optional<DetailedType>> bound;

        public WildCardTyp(ImmutablePair<String, Optional<DetailedType>> t) {
            this.bound = t;
        }

        public ImmutablePair<String, Optional<DetailedType>> getBound() {
            return bound;
        }

        @Override
        public TypeKind getTypeKind() {
            return TypeKind.WildcardType;
        }

        @Override
        public String asStr() {
            return "? " + bound.getLeft() + " " + bound.getRight();
        }
    }

    public static class PrimitiveTyp extends DetailedType{
        private String prmtv;

        public PrimitiveTyp(String t) {
            this.prmtv = t;
        }

        public String getPrmtv() {
            return prmtv;
        }

        @Override
        public TypeKind getTypeKind() {
            return TypeKind.PrimitiveType;
        }

        @Override
        public String asStr() {
            return prmtv;
        }
    }

    public static class ArrayTyp extends DetailedType{
        private DetailedType type;
        private int dim;

        public ArrayTyp(DetailedType type, int dim) {
            this.type = type;
            this.dim = dim;
        }

        public DetailedType getType() {
            return type;
        }

        public int getDim() {
            return dim;
        }

        @Override
        public TypeKind getTypeKind() {
            return TypeKind.ArrayType;
        }

        @Override
        public String asStr() {
            return type + IntStream.range(0, dim).mapToObj(i -> "[]").collect(joining(","));
        }
    }

    public static class InterSectionTyp extends DetailedType{


        private List<DetailedType> intxnTyps;

        public InterSectionTyp(List<DetailedType> t) {
            this.intxnTyps = t;
        }

        public List<DetailedType> getIntxnTyps() {
            return intxnTyps;
        }

        @Override
        public TypeKind getTypeKind() {
            return TypeKind.IntersectionType;
        }

        @Override
        public String asStr() {
            return intxnTyps.stream().map(DetailedType::asStr).collect(joining("&"));
        }
    }

    public static class UnionTyp extends DetailedType{


        private List<DetailedType> unionTyps;

        public UnionTyp(List<DetailedType> t) {
            this.unionTyps = t;
        }

        @Override
        public TypeKind getTypeKind() {
            return TypeKind.UnionType;
        }

        @Override
        public String asStr() {
            return unionTyps.stream().map(DetailedType::asStr).collect(joining("|"));
        }

        public List<DetailedType> getUnionTyps() {
            return unionTyps;
        }
    }

    public enum TypeKind{
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

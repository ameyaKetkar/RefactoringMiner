package gr.uom.java.xmi;

import static java.util.stream.Collectors.joining;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.IntersectionType;
import org.eclipse.jdt.core.dom.NameQualifiedType;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.QualifiedType;
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
        private List<String> annotation;

        public SimpleTyp(SimpleType st) {

            List<Annotation> ann = st.annotations();
            annotation = ann.stream().map(a -> "@" + a.getTypeName().getFullyQualifiedName())
                    .collect(Collectors.toList());
            this.name = st.getName().getFullyQualifiedName();
        }
        public SimpleTyp(String name, boolean isQualified){
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
            return this.name + String.join(" ", annotation);
        }
    }

    public static class ParameterizedTyp extends DetailedType{


        private DetailedType name;
        private List<DetailedType> params;

        public ParameterizedTyp(ParameterizedType pt) {
            this.name = DetailedType.getDetailedType(pt.getType());
            List<Type> ps = pt.typeArguments();
            this.params = ps.stream().map(DetailedType::getDetailedType).collect(Collectors.toList());
        }

        public DetailedType getName() {
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
            return name.asStr() + "<" + params.stream().map(d->d.asStr()).collect(joining(",")) + ">";
        }
    }

    public static class WildCardTyp extends DetailedType{
        private final List<String> annotation;
        private ImmutablePair<String,Optional<DetailedType>> bound;


        public WildCardTyp(WildcardType wt) {
            this.bound = ImmutablePair.of(wt.isUpperBound()?"super":"extends", Optional.ofNullable(wt.getBound()).map(DetailedType::getDetailedType));
            List<Annotation> ann = wt.annotations();
            annotation = ann.stream().map(a -> "@" + a.getTypeName().getFullyQualifiedName())
                    .collect(Collectors.toList());
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
            if(bound.getRight().isPresent()){
                return "? " + bound.getLeft() + " " + bound.getRight().get().asStr()  + String.join(" ", annotation);
            }else{
                return "?" +  String.join(" ", annotation);
            }

        }
    }

    public static class PrimitiveTyp extends DetailedType{
        private final List<String> annotation;
        private String prmtv;

        public PrimitiveTyp(PrimitiveType pt) {
            this.prmtv = pt.getPrimitiveTypeCode().toString();
            List<Annotation> ann = pt.annotations();
            annotation = ann.stream().map(a -> "@" + a.getTypeName().getFullyQualifiedName())
                    .collect(Collectors.toList());
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
            return prmtv+  String.join(" ", annotation);
        }
    }

    public static class ArrayTyp extends DetailedType{
        private DetailedType type;
        private int dim;

        public ArrayTyp(ArrayType at) {
            this.type = getDetailedType(at.getElementType());
            this.dim = at.dimensions().size();
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
            return type.asStr() + IntStream.range(0, dim).mapToObj(i -> "[]").collect(joining(","));
        }
    }

    public static class InterSectionTyp extends DetailedType{

        private List<DetailedType> intxnTyps;

        public InterSectionTyp(IntersectionType it) {
            List<Type> ts = it.types();
            this.intxnTyps = ts.stream().map(z->getDetailedType(z)).collect(Collectors.toList());
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

        public UnionTyp(UnionType ut) {
            List<Type> ts = ut.types();
            this.unionTyps = ts.stream().map(z->getDetailedType(z)).collect(Collectors.toList());
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

    public static class QualifiedTyp extends DetailedType{


        private final List<String> annotation;
        private String qName;
        private DetailedType qualifier;

        public QualifiedTyp(QualifiedType q) {
            qName = q.getName().getIdentifier();
            qualifier = DetailedType.getDetailedType(q.getQualifier());
            List<Annotation> ann = q.annotations();
            annotation = ann.stream().map(a -> "@" + a.getTypeName().getFullyQualifiedName())
                    .collect(Collectors.toList());
        }

        @Override
        public TypeKind getTypeKind() {
            return TypeKind.QualifiedType;
        }

        @Override
        public String asStr() {
            return qName + qualifier.asStr() +  String.join(" ", annotation);
        }

    }

    public static class NameQualifiedTyp extends DetailedType{
        private final List<String> annotation;
        private String name;
        private String qn ;

        public NameQualifiedTyp(NameQualifiedType nq) {
            name = nq.getName().getIdentifier();
            qn = nq.getQualifier().getFullyQualifiedName();
            List<Annotation> ann = nq.annotations();
            annotation = ann.stream().map(a -> "@" + a.getTypeName().getFullyQualifiedName())
                    .collect(Collectors.toList());
        }

        @Override
        public TypeKind getTypeKind() {
            return TypeKind.UnionType;
        }

        @Override
        public String asStr() {
            return name + qn  + String.join(" ", annotation);
        }
    }

    public enum TypeKind{
        SimpleType,
        ParameterizedType,
        UnionType,
        IntersectionType,
        PrimitiveType,
        ArrayType,
        WildcardType,
        QualifiedType,
        NameQualifiedType

    }

    public static DetailedType getDetailedType(Type t) {
        if(t.isSimpleType())
            return new SimpleTyp( (SimpleType) t);
        else if(t.isParameterizedType())
            return new ParameterizedTyp((ParameterizedType) t);
        else if(t.isWildcardType())
            return new WildCardTyp( (WildcardType) t);
        else if(t.isPrimitiveType())
            return new PrimitiveTyp((PrimitiveType) t);
        else if(t.isArrayType())
            return new ArrayTyp((ArrayType) t);
        else if(t.isIntersectionType())
            return new InterSectionTyp((IntersectionType) t);
        else if(t.isUnionType())
            return new UnionTyp((UnionType) t);
        else if(t.isQualifiedType())
            return new QualifiedTyp((QualifiedType) t);
        else if(t.isNameQualifiedType())
            return new NameQualifiedTyp((NameQualifiedType) t);
        else
            return new SimpleTyp(t.toString(), false);
    }
}

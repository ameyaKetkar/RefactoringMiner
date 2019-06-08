package gr.uom.java.xmi;

import static java.util.stream.Collectors.joining;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.Dimension;
import org.eclipse.jdt.core.dom.IntersectionType;
import org.eclipse.jdt.core.dom.NameQualifiedType;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.UnionType;
import org.eclipse.jdt.core.dom.WildcardType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public abstract class DetailedTypeeee {

    public abstract TypeKind getTypeKind();
    public abstract String asStr();

    public static class SimpleTyp extends DetailedTypeeee{
        private final String name;
        public final List<String> annotation;
        public final boolean isQualified;
        public SimpleTyp(SimpleType st) {
            isQualified = st.getName().isQualifiedName();
            List<Annotation> ann = st.annotations();
            annotation = ann.stream().map(a -> "@" + a.getTypeName().getFullyQualifiedName())
                    .collect(Collectors.toList());
            this.name = st.getName().getFullyQualifiedName();
        }


        @Override
        public TypeKind getTypeKind() {
            return TypeKind.SimpleType;
        }

        public String asStr(){
            return name  + String.join(" ", annotation);
        }

        public String getName(){
            return this.name;
        }
    }

    public static class ParameterizedTyp extends DetailedTypeeee{
        private DetailedTypeeee name;
        private List<DetailedTypeeee> params;

        public ParameterizedTyp(ParameterizedType pt) {
            this.name = DetailedTypeeee.getDetailedTypeeee(pt.getType());
            List<Type> ps = pt.typeArguments();
            this.params = ps.stream().map(DetailedTypeeee::getDetailedTypeeee).collect(Collectors.toList());
        }

        public DetailedTypeeee getName() {
            return name;
        }

        public List<DetailedTypeeee> getParams() {
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

    public static class WildCardTyp extends DetailedTypeeee{
        public final List<String> annotation;
        private ImmutablePair<String,Optional<DetailedTypeeee>> bound;


        public WildCardTyp(WildcardType wt) {
            this.bound = ImmutablePair.of(wt.isUpperBound()?"super":"extends", Optional.ofNullable(wt.getBound()).map(DetailedTypeeee::getDetailedTypeeee));
            List<Annotation> ann = wt.annotations();
            annotation = ann.stream().map(a -> "@" + a.getTypeName().getFullyQualifiedName())
                    .collect(Collectors.toList());
        }

        public ImmutablePair<String, Optional<DetailedTypeeee>> getBound() {
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

    public static class PrimitiveTyp extends DetailedTypeeee{
        public final List<String> annotation;
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

    public static class ArrayTyp extends DetailedTypeeee{
        private DetailedTypeeee type;
        private int dim;
        public List<String> annotation = new ArrayList<>();

        public ArrayTyp(ArrayType at) {
            List<Dimension> ds = at.dimensions();

            for(Dimension d : ds){
                List<Annotation> aa = d.annotations();
                for(Annotation a: aa){
                    annotation.add("@" + a.getTypeName().getFullyQualifiedName());
                }
            }

            this.type = getDetailedTypeeee(at.getElementType());
            this.dim = at.dimensions().size();
        }

        public DetailedTypeeee getType() {
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
            return type.asStr() + IntStream.range(0, dim).mapToObj(i -> "[]").collect(joining(","))
                    + String.join(" ", annotation);
        }
    }

    public static class InterSectionTyp extends DetailedTypeeee{

        private List<DetailedTypeeee> intxnTyps;

        public InterSectionTyp(IntersectionType it) {
            List<Type> ts = it.types();
            this.intxnTyps = ts.stream().map(z->getDetailedTypeeee(z)).collect(Collectors.toList());
        }

        public List<DetailedTypeeee> getIntxnTyps() {
            return intxnTyps;
        }

        @Override
        public TypeKind getTypeKind() {
            return TypeKind.IntersectionType;
        }

        @Override
        public String asStr() {
            return intxnTyps.stream().map(DetailedTypeeee::asStr).collect(joining("&"));
        }
    }

    public static class UnionTyp extends DetailedTypeeee{


        private List<DetailedTypeeee> unionTyps;

        public UnionTyp(UnionType ut) {
            List<Type> ts = ut.types();
            this.unionTyps = ts.stream().map(z->getDetailedTypeeee(z)).collect(Collectors.toList());
        }

        @Override
        public TypeKind getTypeKind() {
            return TypeKind.UnionType;
        }

        @Override
        public String asStr() {
            return unionTyps.stream().map(DetailedTypeeee::asStr).collect(joining("|"));
        }

        public List<DetailedTypeeee> getUnionTyps() {
            return unionTyps;
        }
    }

    public static class QualifiedTyp extends DetailedTypeeee{


        public final List<String> annotation;
        public final String qName;
        public final DetailedTypeeee qualifier;

        public QualifiedTyp(QualifiedType q) {
            qName = q.getName().getIdentifier();
            qualifier = DetailedTypeeee.getDetailedTypeeee(q.getQualifier());
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

    public static class NameQualifiedTyp extends DetailedTypeeee{
        public final List<String> annotation;
        public String name;
        public String qn ;

        public NameQualifiedTyp(NameQualifiedType nq) {
            name = nq.getName().getIdentifier();
            qn = nq.getQualifier().getFullyQualifiedName();
            List<Annotation> ann = nq.annotations();
            annotation = ann.stream().map(a -> "@" + a.getTypeName().getFullyQualifiedName())
                    .collect(Collectors.toList());
        }

        @Override
        public TypeKind getTypeKind() {
            return TypeKind.NameQualifiedType;
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

    public static DetailedTypeeee getDetailedTypeeee(Type t) {
        if(t.isQualifiedType())
            return new QualifiedTyp((QualifiedType) t);
        else if(t.isNameQualifiedType())
            return new NameQualifiedTyp((NameQualifiedType) t);
        else if(t.isSimpleType())
            return new SimpleTyp((SimpleType) t);
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
        else
            throw new RuntimeException("Could not figure out type");

    }
}

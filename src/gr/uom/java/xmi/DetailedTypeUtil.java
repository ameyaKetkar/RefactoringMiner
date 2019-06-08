package gr.uom.java.xmi;


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
import org.refactoringminer.Models.DetailedTypeOuterClass.DetailedType;
import org.refactoringminer.Models.DetailedTypeOuterClass.DetailedType.ArryTyp;
import org.refactoringminer.Models.DetailedTypeOuterClass.DetailedType.IntersxnTyp;
import org.refactoringminer.Models.DetailedTypeOuterClass.DetailedType.ParamType;
import org.refactoringminer.Models.DetailedTypeOuterClass.DetailedType.Prmtv;
import org.refactoringminer.Models.DetailedTypeOuterClass.DetailedType.QualifiedTyp;
import org.refactoringminer.Models.DetailedTypeOuterClass.DetailedType.SimplType;
import org.refactoringminer.Models.DetailedTypeOuterClass.DetailedType.TypeOfType;
import org.refactoringminer.Models.DetailedTypeOuterClass.DetailedType.UnionTyp;
import org.refactoringminer.Models.DetailedTypeOuterClass.DetailedType.WildCrdType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class DetailedTypeUtil {

    public static DetailedType getDetailedType(SimpleType st){
        List<Annotation> ann = st.annotations();
        List<String> annotation = ann.stream().map(a -> "@" + a.getTypeName().getFullyQualifiedName())
                    .collect(Collectors.toList());
        String name = st.getName().getFullyQualifiedName();
        return DetailedType.newBuilder().setSimpleType(SimplType.newBuilder()
                .setName(name)
                .addAllAnnotations(annotation).build()).build();
    }

    public static DetailedType getDetailedType(ParameterizedType pt){
        List<Type> ps = pt.typeArguments();
        List<DetailedType>  params = ps.stream().map(DetailedTypeUtil::getDetailedType).collect(Collectors.toList());
        return DetailedType.newBuilder()
                .setParamType(ParamType.newBuilder().setName(getDetailedType(pt.getType()))
                        .addAllParams(params)).build();
    }

    public static DetailedType getDetailedType(WildcardType wt){
        ImmutablePair<String, Optional<DetailedType>> bound =
                ImmutablePair.of(wt.isUpperBound() ? "super" : "extends", Optional.ofNullable(wt.getBound()).map(DetailedTypeUtil::getDetailedType));
        List<Annotation> ann = wt.annotations();
        List<String> annotation = ann.stream().map(a -> "@" + a.getTypeName().getFullyQualifiedName())
                .collect(Collectors.toList());
        return bound.right.map(detailedType -> DetailedType.newBuilder()
                .setWildCrd(WildCrdType.newBuilder().setBound(detailedType)
                        .addAllAnnotations(annotation)
                        .setSupOrext(bound.left).build()).build())
                .orElseGet(() -> DetailedType.newBuilder()
                        .setWildCrd(WildCrdType.newBuilder()
                        .addAllAnnotations(annotation).setSupOrext(bound.left).build()).build());


    }

    public static DetailedType getDetailedType(PrimitiveType pt){
        List<Annotation> ann = pt.annotations();
        List<String> annotation = ann.stream().map(a -> "@" + a.getTypeName().getFullyQualifiedName())
                .collect(Collectors.toList());
        return DetailedType.newBuilder()
                .setPrmtv(Prmtv.newBuilder().addAllAnnotations(annotation)
                        .setName(pt.getPrimitiveTypeCode().toString()).build()).build();
    }

    public static DetailedType getDetailedType(ArrayType at){
        List<Dimension> ds = at.dimensions();
        List<String> annotation = new ArrayList<>();
        for(Dimension d : ds){
            List<Annotation> aa = d.annotations();
            for(Annotation a: aa){
                annotation.add("@" + a.getTypeName().getFullyQualifiedName());
            }
        }
        return DetailedType.newBuilder()
                .setArryTyp(ArryTyp.newBuilder().addAllAnnotations(annotation).setDim(at.dimensions().size())
                .setName(getDetailedType(at.getElementType()))).build();
    }

    public static DetailedType getDetailedType(IntersectionType it){
        List<Type> ts = it.types();
        return DetailedType.newBuilder()
                .setIntxnTyp(IntersxnTyp.newBuilder()
                        .addAllTypes(ts.stream().map(z->getDetailedType(z)).collect(Collectors.toList()))).build();
    }

    public static DetailedType getDetailedType(UnionType it){
        List<Type> ts = it.types();
        return DetailedType.newBuilder()
                .setUnionTyp(UnionTyp.newBuilder()
                        .addAllTypes(ts.stream().map(z->getDetailedType(z)).collect(Collectors.toList()))).build();
    }

    public static DetailedType getDetailedType(QualifiedType q){
        List<Annotation> ann = q.annotations();
        List<String> annotation = ann.stream().map(a -> "@" + a.getTypeName().getFullyQualifiedName())
                .collect(Collectors.toList());
        return DetailedType.newBuilder()
                .setQualTyp(QualifiedTyp.newBuilder().setName(q.getName().getIdentifier())
                .setQualifier(getDetailedType(q.getQualifier())).addAllAnnotations(annotation))
                .build();
    }

    public static DetailedType getDetailedType(NameQualifiedType nq){
        List<Annotation> ann = nq.annotations();
        List<String> annotation = ann.stream().map(a -> "@" + a.getTypeName().getFullyQualifiedName())
                .collect(Collectors.toList());
        return DetailedType.newBuilder()
                .setSimpleType(SimplType.newBuilder().setName(nq.getQualifier().getFullyQualifiedName() + "." + nq.getName().getIdentifier())
                        .addAllAnnotations(annotation).build()).build();
    }


    public static DetailedType getDetailedType(Type t) {
        if(t.isQualifiedType())
            return getDetailedType((QualifiedType) t);
        else if(t.isNameQualifiedType())
            return getDetailedType((NameQualifiedType) t);
        else if(t.isSimpleType())
            return getDetailedType((SimpleType) t);
        else if(t.isParameterizedType())
            return getDetailedType((ParameterizedType) t);
        else if(t.isWildcardType())
            return getDetailedType( (WildcardType) t);
        else if(t.isPrimitiveType())
            return getDetailedType((PrimitiveType) t);
        else if(t.isArrayType())
            return getDetailedType((ArrayType) t);
        else if(t.isIntersectionType())
            return getDetailedType((IntersectionType) t);
        else if(t.isUnionType())
            return getDetailedType((UnionType) t);
        else
            throw new RuntimeException("Could not figure out type");

    }

    public static DetailedType getDetailedType(String s){
        return DetailedType.newBuilder().setSimpleType(SimplType.newBuilder().setName(s).build()).build();
    }


    public static TypeOfType getTypeOfTypeFor(DetailedType dt){
        if(dt.hasSimpleType())
            return dt.getSimpleType().getTypeOfType();
        else if (dt.hasParamType())
            return getTypeOfTypeFor(dt.getParamType().getName());
        else if(dt.hasPrmtv())
            return dt.getPrmtv().getTypeOfType();
        else if(dt.hasQualTyp())
            return getTypeOfTypeFor(dt.getQualTyp().getQualifier());
        else if(dt.hasArryTyp())
            return getTypeOfTypeFor(dt.getArryTyp().getName());
        else return null;
    }




}

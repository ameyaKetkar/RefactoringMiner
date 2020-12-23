package gr.uom.java.xmi;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IExtendedModifier;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

import gr.uom.java.xmi.LocationInfo.CodeElementType;
import gr.uom.java.xmi.decomposition.OperationBody;
import gr.uom.java.xmi.decomposition.VariableDeclaration;

public class UMLModelASTReader {
	private static final String FREE_MARKER_GENERATED = "generated using freemarker";
	private UMLModel umlModel;

	public UMLModelASTReader(Map<String, String> javaFileContents, Set<String> repositoryDirectories) {
		this.umlModel = new UMLModel(repositoryDirectories);

		Map<String, String> options = getCompilerOptions();
		this.getUmlModel().addClasses(javaFileContents.entrySet().parallelStream()
				.filter(content -> !content.getValue().contains(FREE_MARKER_GENERATED))
				.flatMap(x -> {
					ASTParser parser = getAstParser(options);
					parser.setSource(x.getValue().toCharArray());
					CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
					// I dont know why u have this try and catch, albeit i put it
					try {
						return processCompilationUnit(x.getKey(), compilationUnit, x.getValue());
					} catch (Exception e) {
						return Stream.empty();
					}
				}).collect(Collectors.toList()));

	}

	private ASTParser getAstParser(Map<String, String> options) {
		ASTParser parser = ASTParser.newParser(AST.JLS14);
		parser.setCompilerOptions(options);
		parser.setResolveBindings(false);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setStatementsRecovery(true);
		return parser;
	}

	private Map<String, String> getCompilerOptions() {
		Map<String, String> options = JavaCore.getOptions();
		options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_1_8);
		options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
		options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_8);
		return options;
	}


	public UMLModel getUmlModel() {
		return this.umlModel;
	}

	protected Stream<UMLClass> processCompilationUnit(String sourceFilePath, CompilationUnit compilationUnit, String javaFileContent) {
		List<UMLComment> comments = extractInternalComments(compilationUnit, sourceFilePath, javaFileContent);
		PackageDeclaration packageDeclaration = compilationUnit.getPackage();
		String packageName = packageDeclaration != null ?  packageDeclaration.getName().getFullyQualifiedName() : "";

		List<ImportDeclaration> imports = compilationUnit.imports();

		List<String> importedTypes = imports.stream().map(i -> i.getName().getFullyQualifiedName())
				.collect(Collectors.toList());

		List<AbstractTypeDeclaration> topLevelTypeDeclarations = compilationUnit.types();

		Function<AbstractTypeDeclaration, Stream<UMLClass>> process = x -> {
			if (x instanceof TypeDeclaration)
				return processTypeDeclaration(compilationUnit, (TypeDeclaration) x, packageName, sourceFilePath, importedTypes);
			else if(x instanceof EnumDeclaration)
				return processEnumDeclaration(compilationUnit, (EnumDeclaration) x, packageName, sourceFilePath, importedTypes).stream();
			return Stream.empty();
		};

		return topLevelTypeDeclarations.stream()
				.flatMap(process);


//        for(AbstractTypeDeclaration abstractTypeDeclaration : topLevelTypeDeclarations) {
//        	if(abstractTypeDeclaration instanceof TypeDeclaration) {
//        		TypeDeclaration topLevelTypeDeclaration = (TypeDeclaration)abstractTypeDeclaration;
//        		classList.addAll(processTypeDeclaration(compilationUnit, topLevelTypeDeclaration, packageName, sourceFilePath, importedTypes));
//        	}
//        	else if(abstractTypeDeclaration instanceof EnumDeclaration) {
//        		EnumDeclaration enumDeclaration = (EnumDeclaration)abstractTypeDeclaration;
//        		processEnumDeclaration(compilationUnit, enumDeclaration, packageName, sourceFilePath, importedTypes)
//						.ifPresent(classList::add);
//        	}
//        }
	}

	private List<UMLComment> extractInternalComments(CompilationUnit cu, String sourceFile, String javaFileContent) {
		List<Comment> astComments = cu.getCommentList();
		List<UMLComment> comments = new ArrayList<UMLComment>();
		for(Comment comment : astComments) {
			LocationInfo locationInfo = null;
			if(comment.isLineComment()) {
				locationInfo = generateLocationInfo(cu, sourceFile, comment, CodeElementType.LINE_COMMENT);
			}
			else if(comment.isBlockComment()) {
				locationInfo = generateLocationInfo(cu, sourceFile, comment, CodeElementType.BLOCK_COMMENT);
			}
			if(locationInfo != null) {
				int start = comment.getStartPosition();
				int end = start + comment.getLength();
				String text = javaFileContent.substring(start, end);
				UMLComment umlComment = new UMLComment(text, locationInfo);
				comments.add(umlComment);
			}
		}
		return comments;
	}

	private UMLJavadoc generateJavadoc(CompilationUnit cu, BodyDeclaration bodyDeclaration, String sourceFile) {
		UMLJavadoc doc = null;
		Javadoc javaDoc = bodyDeclaration.getJavadoc();
		if(javaDoc != null) {
			LocationInfo locationInfo = generateLocationInfo(cu, sourceFile, javaDoc, CodeElementType.JAVADOC);
			doc = new UMLJavadoc(locationInfo);
			List<TagElement> tags = javaDoc.tags();
			for(TagElement tag : tags) {
				UMLTagElement tagElement = new UMLTagElement(tag.getTagName());
				List fragments = tag.fragments();
				for(Object docElement : fragments) {
					tagElement.addFragment(docElement.toString());
				}
				doc.addTag(tagElement);
			}
		}
		return doc;
	}

	private Optional<UMLClass> processEnumDeclaration(CompilationUnit cu, EnumDeclaration enumDeclaration, String packageName, String sourceFile,
											List<String> importedTypes) {
		UMLJavadoc javadoc = generateJavadoc(cu, enumDeclaration, sourceFile);
		if(javadoc != null && javadoc.containsIgnoreCase(FREE_MARKER_GENERATED)) {
			return Optional.empty();
		}
		String className = enumDeclaration.getName().getFullyQualifiedName();
		LocationInfo locationInfo = generateLocationInfo(cu, sourceFile, enumDeclaration, CodeElementType.TYPE_DECLARATION);
		UMLClass umlClass = new UMLClass(packageName, className, locationInfo, enumDeclaration.isPackageMemberTypeDeclaration(), importedTypes);
		umlClass.setJavadoc(javadoc);
		
		umlClass.setEnum(true);
		
		List<Type> superInterfaceTypes = enumDeclaration.superInterfaceTypes();
    	for(Type interfaceType : superInterfaceTypes) {
    		UMLType umlType = UMLType.extractTypeObject(cu, sourceFile, interfaceType, 0);
    		UMLRealization umlRealization = new UMLRealization(umlClass, umlType.getClassType());
    		umlClass.addImplementedInterface(umlType);
    		getUmlModel().addRealization(umlRealization);
    	}
    	
    	List<EnumConstantDeclaration> enumConstantDeclarations = enumDeclaration.enumConstants();
    	for(EnumConstantDeclaration enumConstantDeclaration : enumConstantDeclarations) {
			processEnumConstantDeclaration(cu, enumConstantDeclaration, sourceFile, umlClass);
		}
		
		processModifiers(cu, sourceFile, enumDeclaration, umlClass);
		
		processBodyDeclarations(cu, enumDeclaration, packageName, sourceFile, importedTypes, umlClass);
		
		processAnonymousClassDeclarations(cu, enumDeclaration, packageName, sourceFile, className, umlClass);
		return Optional.of(umlClass);
		
		//this.getUmlModel().addClass(umlClass);
	}

	private void processBodyDeclarations(CompilationUnit cu, AbstractTypeDeclaration abstractTypeDeclaration, String packageName,
			String sourceFile, List<String> importedTypes, UMLClass umlClass) {
		List<BodyDeclaration> bodyDeclarations = abstractTypeDeclaration.bodyDeclarations();
		for(BodyDeclaration bodyDeclaration : bodyDeclarations) {
			if(bodyDeclaration instanceof FieldDeclaration) {
				FieldDeclaration fieldDeclaration = (FieldDeclaration)bodyDeclaration;
				List<UMLAttribute> attributes = processFieldDeclaration(cu, fieldDeclaration, umlClass.isInterface(), sourceFile);
	    		for(UMLAttribute attribute : attributes) {
	    			attribute.setClassName(umlClass.getName());
	    			umlClass.addAttribute(attribute);
	    		}
			}
			else if(bodyDeclaration instanceof MethodDeclaration) {
				MethodDeclaration methodDeclaration = (MethodDeclaration)bodyDeclaration;
				UMLOperation operation = processMethodDeclaration(cu, methodDeclaration, packageName, umlClass.isInterface(), sourceFile);
	    		operation.setClassName(umlClass.getName());
	    		umlClass.addOperation(operation);
			}
			else if(bodyDeclaration instanceof TypeDeclaration) {
				TypeDeclaration typeDeclaration = (TypeDeclaration)bodyDeclaration;
				processTypeDeclaration(cu, typeDeclaration, umlClass.getName(), sourceFile, importedTypes);
			}
			else if(bodyDeclaration instanceof EnumDeclaration) {
				EnumDeclaration enumDeclaration = (EnumDeclaration)bodyDeclaration;
				processEnumDeclaration(cu, enumDeclaration, umlClass.getName(), sourceFile, importedTypes);
			}
		}
	}

	private Stream<UMLClass> processTypeDeclaration(CompilationUnit cu, TypeDeclaration typeDeclaration, String packageName, String sourceFile,
													 List<String> importedTypes) {

		var classList = new ArrayList<UMLClass>();

		UMLJavadoc javadoc = generateJavadoc(cu, typeDeclaration, sourceFile);
		if(javadoc != null && javadoc.containsIgnoreCase(FREE_MARKER_GENERATED)) {
			return Stream.empty();
		}
		String className = typeDeclaration.getName().getFullyQualifiedName();
		LocationInfo locationInfo = generateLocationInfo(cu, sourceFile, typeDeclaration, CodeElementType.TYPE_DECLARATION);
		UMLClass umlClass = new UMLClass(packageName, className, locationInfo, typeDeclaration.isPackageMemberTypeDeclaration(), importedTypes);
		umlClass.setJavadoc(javadoc);
		
		if(typeDeclaration.isInterface()) {
			umlClass.setInterface(true);
    	}
    	
    	processModifiers(cu, sourceFile, typeDeclaration, umlClass);
		
    	List<TypeParameter> typeParameters = typeDeclaration.typeParameters();
		for(TypeParameter typeParameter : typeParameters) {
			UMLTypeParameter umlTypeParameter = new UMLTypeParameter(typeParameter.getName().getFullyQualifiedName());
			List<Type> typeBounds = typeParameter.typeBounds();
			for(Type type : typeBounds) {
				umlTypeParameter.addTypeBound(UMLType.extractTypeObject(cu, sourceFile, type, 0));
			}
			List<IExtendedModifier> typeParameterExtendedModifiers = typeParameter.modifiers();
			for(IExtendedModifier extendedModifier : typeParameterExtendedModifiers) {
				if(extendedModifier.isAnnotation()) {
					Annotation annotation = (Annotation)extendedModifier;
					umlTypeParameter.addAnnotation(new UMLAnnotation(cu, sourceFile, annotation));
				}
			}
    		umlClass.addTypeParameter(umlTypeParameter);
    	}
    	
    	Type superclassType = typeDeclaration.getSuperclassType();
    	if(superclassType != null) {
    		UMLType umlType = UMLType.extractTypeObject(cu, sourceFile, superclassType, 0);
    		UMLGeneralization umlGeneralization = new UMLGeneralization(umlClass, umlType.getClassType());
    		umlClass.setSuperclass(umlType);
    		getUmlModel().addGeneralization(umlGeneralization);
    	}
    	
    	List<Type> superInterfaceTypes = typeDeclaration.superInterfaceTypes();
    	for(Type interfaceType : superInterfaceTypes) {
    		UMLType umlType = UMLType.extractTypeObject(cu, sourceFile, interfaceType, 0);
    		UMLRealization umlRealization = new UMLRealization(umlClass, umlType.getClassType());
    		umlClass.addImplementedInterface(umlType);
    		getUmlModel().addRealization(umlRealization);
    	}
    	
    	FieldDeclaration[] fieldDeclarations = typeDeclaration.getFields();
    	for(FieldDeclaration fieldDeclaration : fieldDeclarations) {
    		List<UMLAttribute> attributes = processFieldDeclaration(cu, fieldDeclaration, umlClass.isInterface(), sourceFile);
    		for(UMLAttribute attribute : attributes) {
    			attribute.setClassName(umlClass.getName());
    			umlClass.addAttribute(attribute);
    		}
    	}
    	
    	MethodDeclaration[] methodDeclarations = typeDeclaration.getMethods();
    	for(MethodDeclaration methodDeclaration : methodDeclarations) {
    		UMLOperation operation = processMethodDeclaration(cu, methodDeclaration, packageName, umlClass.isInterface(), sourceFile);
    		operation.setClassName(umlClass.getName());
    		umlClass.addOperation(operation);
    	}
    	
    	processAnonymousClassDeclarations(cu, typeDeclaration, packageName, sourceFile, className, umlClass);

		classList.add(umlClass);
		TypeDeclaration[] types = typeDeclaration.getTypes();
		List<BodyDeclaration> bodyDeclarations = typeDeclaration.bodyDeclarations();

		return Stream.concat(Stream.concat(Stream.of(umlClass)
				, Arrays.stream(types).flatMap(type -> processTypeDeclaration(cu, type, umlClass.getName(), sourceFile, importedTypes)))
				, bodyDeclarations.stream().filter(x -> x instanceof  EnumDeclaration)
						.flatMap(x -> processEnumDeclaration(cu, (EnumDeclaration) x, umlClass.getName(), sourceFile, importedTypes).stream()));
	}

	private void processAnonymousClassDeclarations(CompilationUnit cu, AbstractTypeDeclaration typeDeclaration,
			String packageName, String sourceFile, String className, UMLClass umlClass) {
		AnonymousClassDeclarationVisitor visitor = new AnonymousClassDeclarationVisitor();
    	typeDeclaration.accept(visitor);
    	Set<AnonymousClassDeclaration> anonymousClassDeclarations = visitor.getAnonymousClassDeclarations();
    	
    	DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    	for(AnonymousClassDeclaration anonymous : anonymousClassDeclarations) {
    		insertNode(anonymous, root);
    	}
    	
    	List<UMLAnonymousClass> createdAnonymousClasses = new ArrayList<UMLAnonymousClass>();
    	Enumeration enumeration = root.preorderEnumeration();
    	while(enumeration.hasMoreElements()) {
    		DefaultMutableTreeNode node = (DefaultMutableTreeNode)enumeration.nextElement();
    		if(node.getUserObject() != null) {
    			AnonymousClassDeclaration anonymous = (AnonymousClassDeclaration)node.getUserObject();
    			String anonymousBinaryName = getAnonymousBinaryName(node);
    			String anonymousCodePath = getAnonymousCodePath(node);
    			UMLAnonymousClass anonymousClass = processAnonymousClassDeclaration(cu, anonymous, packageName + "." + className, anonymousBinaryName, anonymousCodePath, sourceFile);
    			umlClass.addAnonymousClass(anonymousClass);
    			for(UMLOperation operation : umlClass.getOperations()) {
    				if(operation.getLocationInfo().subsumes(anonymousClass.getLocationInfo())) {
    					operation.addAnonymousClass(anonymousClass);
    				}
    			}
    			for(UMLAnonymousClass createdAnonymousClass : createdAnonymousClasses) {
    				for(UMLOperation operation : createdAnonymousClass.getOperations()) {
        				if(operation.getLocationInfo().subsumes(anonymousClass.getLocationInfo())) {
        					operation.addAnonymousClass(anonymousClass);
        				}
        			}
    			}
    			createdAnonymousClasses.add(anonymousClass);
    		}
    	}
	}

	private void processModifiers(CompilationUnit cu, String sourceFile, AbstractTypeDeclaration typeDeclaration, UMLClass umlClass) {
		int modifiers = typeDeclaration.getModifiers();
    	if((modifiers & Modifier.ABSTRACT) != 0)
    		umlClass.setAbstract(true);
    	
    	if((modifiers & Modifier.PUBLIC) != 0)
    		umlClass.setVisibility("public");
    	else if((modifiers & Modifier.PROTECTED) != 0)
    		umlClass.setVisibility("protected");
    	else if((modifiers & Modifier.PRIVATE) != 0)
    		umlClass.setVisibility("private");
    	else
    		umlClass.setVisibility("package");
    	
    	List<IExtendedModifier> extendedModifiers = typeDeclaration.modifiers();
		for(IExtendedModifier extendedModifier : extendedModifiers) {
			if(extendedModifier.isAnnotation()) {
				Annotation annotation = (Annotation)extendedModifier;
				umlClass.addAnnotation(new UMLAnnotation(cu, sourceFile, annotation));
			}
		}
	}

	private UMLOperation processMethodDeclaration(CompilationUnit cu, MethodDeclaration methodDeclaration, String packageName, boolean isInterfaceMethod, String sourceFile) {
		UMLJavadoc javadoc = generateJavadoc(cu, methodDeclaration, sourceFile);
		String methodName = methodDeclaration.getName().getFullyQualifiedName();
		LocationInfo locationInfo = generateLocationInfo(cu, sourceFile, methodDeclaration, CodeElementType.METHOD_DECLARATION);
		UMLOperation umlOperation = new UMLOperation(methodName, locationInfo);
		umlOperation.setJavadoc(javadoc);
		
		if(methodDeclaration.isConstructor())
			umlOperation.setConstructor(true);
		
		int methodModifiers = methodDeclaration.getModifiers();
		if((methodModifiers & Modifier.PUBLIC) != 0)
			umlOperation.setVisibility("public");
		else if((methodModifiers & Modifier.PROTECTED) != 0)
			umlOperation.setVisibility("protected");
		else if((methodModifiers & Modifier.PRIVATE) != 0)
			umlOperation.setVisibility("private");
		else if(isInterfaceMethod)
			umlOperation.setVisibility("public");
		else
			umlOperation.setVisibility("package");
		
		if((methodModifiers & Modifier.ABSTRACT) != 0)
			umlOperation.setAbstract(true);
		
		if((methodModifiers & Modifier.FINAL) != 0)
			umlOperation.setFinal(true);
		
		if((methodModifiers & Modifier.STATIC) != 0)
			umlOperation.setStatic(true);
		
		List<IExtendedModifier> extendedModifiers = methodDeclaration.modifiers();
		for(IExtendedModifier extendedModifier : extendedModifiers) {
			if(extendedModifier.isAnnotation()) {
				Annotation annotation = (Annotation)extendedModifier;
				umlOperation.addAnnotation(new UMLAnnotation(cu, sourceFile, annotation));
			}
		}
		
		List<TypeParameter> typeParameters = methodDeclaration.typeParameters();
		for(TypeParameter typeParameter : typeParameters) {
			UMLTypeParameter umlTypeParameter = new UMLTypeParameter(typeParameter.getName().getFullyQualifiedName());
			List<Type> typeBounds = typeParameter.typeBounds();
			for(Type type : typeBounds) {
				umlTypeParameter.addTypeBound(UMLType.extractTypeObject(cu, sourceFile, type, 0));
			}
			List<IExtendedModifier> typeParameterExtendedModifiers = typeParameter.modifiers();
			for(IExtendedModifier extendedModifier : typeParameterExtendedModifiers) {
				if(extendedModifier.isAnnotation()) {
					Annotation annotation = (Annotation)extendedModifier;
					umlTypeParameter.addAnnotation(new UMLAnnotation(cu, sourceFile, annotation));
				}
			}
			umlOperation.addTypeParameter(umlTypeParameter);
		}
		
		Block block = methodDeclaration.getBody();
		if(block != null) {
			OperationBody body = new OperationBody(cu, sourceFile, block);
			umlOperation.setBody(body);
			if(block.statements().size() == 0) {
				umlOperation.setEmptyBody(true);
			}
		}
		else {
			umlOperation.setBody(null);
		}
		
		Type returnType = methodDeclaration.getReturnType2();
		if(returnType != null) {
			UMLType type = UMLType.extractTypeObject(cu, sourceFile, returnType, methodDeclaration.getExtraDimensions());
			UMLParameter returnParameter = new UMLParameter("return", type, "return", false);
			umlOperation.addParameter(returnParameter);
		}
		List<SingleVariableDeclaration> parameters = methodDeclaration.parameters();
		for(SingleVariableDeclaration parameter : parameters) {
			Type parameterType = parameter.getType();
			String parameterName = parameter.getName().getFullyQualifiedName();
			UMLType type = UMLType.extractTypeObject(cu, sourceFile, parameterType, parameter.getExtraDimensions());
			UMLParameter umlParameter = new UMLParameter(parameterName, type, "in", parameter.isVarargs());
			VariableDeclaration variableDeclaration = new VariableDeclaration(cu, sourceFile, parameter, parameter.isVarargs());
			variableDeclaration.setParameter(true);
			umlParameter.setVariableDeclaration(variableDeclaration);
			umlOperation.addParameter(umlParameter);
		}
		return umlOperation;
	}

	private void processEnumConstantDeclaration(CompilationUnit cu, EnumConstantDeclaration enumConstantDeclaration, String sourceFile, UMLClass umlClass) {
		UMLJavadoc javadoc = generateJavadoc(cu, enumConstantDeclaration, sourceFile);
		LocationInfo locationInfo = generateLocationInfo(cu, sourceFile, enumConstantDeclaration, CodeElementType.ENUM_CONSTANT_DECLARATION);
		UMLEnumConstant enumConstant = new UMLEnumConstant(enumConstantDeclaration.getName().getIdentifier(), UMLType.extractTypeObject(umlClass.getName()), locationInfo);
		VariableDeclaration variableDeclaration = new VariableDeclaration(cu, sourceFile, enumConstantDeclaration);
		enumConstant.setVariableDeclaration(variableDeclaration);
		enumConstant.setJavadoc(javadoc);
		enumConstant.setFinal(true);
		enumConstant.setStatic(true);
		enumConstant.setVisibility("public");
		List<Expression> arguments = enumConstantDeclaration.arguments();
		for(Expression argument : arguments) {
			enumConstant.addArgument(argument.toString());
		}
		enumConstant.setClassName(umlClass.getName());
		umlClass.addEnumConstant(enumConstant);
	}

	private List<UMLAttribute> processFieldDeclaration(CompilationUnit cu, FieldDeclaration fieldDeclaration, boolean isInterfaceField, String sourceFile) {
		UMLJavadoc javadoc = generateJavadoc(cu, fieldDeclaration, sourceFile);
		List<UMLAttribute> attributes = new ArrayList<UMLAttribute>();
		Type fieldType = fieldDeclaration.getType();
		List<VariableDeclarationFragment> fragments = fieldDeclaration.fragments();
		for(VariableDeclarationFragment fragment : fragments) {
			UMLType type = UMLType.extractTypeObject(cu, sourceFile, fieldType, fragment.getExtraDimensions());
			String fieldName = fragment.getName().getFullyQualifiedName();
			LocationInfo locationInfo = generateLocationInfo(cu, sourceFile, fragment, CodeElementType.FIELD_DECLARATION);
			UMLAttribute umlAttribute = new UMLAttribute(fieldName, type, locationInfo);
			VariableDeclaration variableDeclaration = new VariableDeclaration(cu, sourceFile, fragment);
			variableDeclaration.setAttribute(true);
			umlAttribute.setVariableDeclaration(variableDeclaration);
			umlAttribute.setJavadoc(javadoc);
			
			int fieldModifiers = fieldDeclaration.getModifiers();
			if((fieldModifiers & Modifier.PUBLIC) != 0)
				umlAttribute.setVisibility("public");
			else if((fieldModifiers & Modifier.PROTECTED) != 0)
				umlAttribute.setVisibility("protected");
			else if((fieldModifiers & Modifier.PRIVATE) != 0)
				umlAttribute.setVisibility("private");
			else if(isInterfaceField)
				umlAttribute.setVisibility("public");
			else
				umlAttribute.setVisibility("package");
			
			if((fieldModifiers & Modifier.FINAL) != 0)
				umlAttribute.setFinal(true);
			
			if((fieldModifiers & Modifier.STATIC) != 0)
				umlAttribute.setStatic(true);
			
			attributes.add(umlAttribute);
		}
		return attributes;
	}
	
	private UMLAnonymousClass processAnonymousClassDeclaration(CompilationUnit cu, AnonymousClassDeclaration anonymous, String packageName, String binaryName, String codePath, String sourceFile) {
		List<BodyDeclaration> bodyDeclarations = anonymous.bodyDeclarations();
		LocationInfo locationInfo = generateLocationInfo(cu, sourceFile, anonymous, CodeElementType.ANONYMOUS_CLASS_DECLARATION);
		UMLAnonymousClass anonymousClass = new UMLAnonymousClass(packageName, binaryName, codePath, locationInfo);
		
		for(BodyDeclaration bodyDeclaration : bodyDeclarations) {
			if(bodyDeclaration instanceof FieldDeclaration) {
				FieldDeclaration fieldDeclaration = (FieldDeclaration)bodyDeclaration;
				List<UMLAttribute> attributes = processFieldDeclaration(cu, fieldDeclaration, false, sourceFile);
	    		for(UMLAttribute attribute : attributes) {
	    			attribute.setClassName(anonymousClass.getCodePath());
	    			anonymousClass.addAttribute(attribute);
	    		}
			}
			else if(bodyDeclaration instanceof MethodDeclaration) {
				MethodDeclaration methodDeclaration = (MethodDeclaration)bodyDeclaration;
				UMLOperation operation = processMethodDeclaration(cu, methodDeclaration, packageName, false, sourceFile);
				operation.setClassName(anonymousClass.getCodePath());
				anonymousClass.addOperation(operation);
			}
		}
		
		return anonymousClass;
	}
	
	private void insertNode(AnonymousClassDeclaration childAnonymous, DefaultMutableTreeNode root) {
		Enumeration enumeration = root.postorderEnumeration();
		DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(childAnonymous);
		
		DefaultMutableTreeNode parentNode = root;
		while(enumeration.hasMoreElements()) {
			DefaultMutableTreeNode currentNode = (DefaultMutableTreeNode)enumeration.nextElement();
			AnonymousClassDeclaration currentAnonymous = (AnonymousClassDeclaration)currentNode.getUserObject();
			if(currentAnonymous != null && isParent(childAnonymous, currentAnonymous)) {
				parentNode = currentNode;
				break;
			}
		}
		parentNode.add(childNode);
	}

	private String getAnonymousCodePath(DefaultMutableTreeNode node) {
		AnonymousClassDeclaration anonymous = (AnonymousClassDeclaration)node.getUserObject();
		String name = "";
		ASTNode parent = anonymous.getParent();
		while(parent != null) {
			if(parent instanceof MethodDeclaration) {
				String methodName = ((MethodDeclaration)parent).getName().getIdentifier();
				if(name.isEmpty()) {
					name = methodName;
				}
				else {
					name = methodName + "." + name;
				}
			}
			else if(parent instanceof VariableDeclarationFragment &&
					(parent.getParent() instanceof FieldDeclaration ||
					parent.getParent() instanceof VariableDeclarationStatement)) {
				String fieldName = ((VariableDeclarationFragment)parent).getName().getIdentifier();
				if(name.isEmpty()) {
					name = fieldName;
				}
				else {
					name = fieldName + "." + name;
				}
			}
			else if(parent instanceof MethodInvocation) {
				String invocationName = ((MethodInvocation)parent).getName().getIdentifier();
				if(name.isEmpty()) {
					name = invocationName;
				}
				else {
					name = invocationName + "." + name;
				}
			}
			else if(parent instanceof SuperMethodInvocation) {
				String invocationName = ((SuperMethodInvocation)parent).getName().getIdentifier();
				if(name.isEmpty()) {
					name = invocationName;
				}
				else {
					name = invocationName + "." + name;
				}
			}
			parent = parent.getParent();
		}
		return name.toString();
	}

	private String getAnonymousBinaryName(DefaultMutableTreeNode node) {
		StringBuilder name = new StringBuilder();
		TreeNode[] path = node.getPath();
		for(int i=0; i<path.length; i++) {
			DefaultMutableTreeNode tmp = (DefaultMutableTreeNode)path[i];
			if(tmp.getUserObject() != null) {
				DefaultMutableTreeNode parent = (DefaultMutableTreeNode)tmp.getParent();
				int index = parent.getIndex(tmp);
				name.append(index+1);
				if(i < path.length-1)
					name.append(".");
			}
		}
		return name.toString();
	}
	
	private boolean isParent(ASTNode child, ASTNode parent) {
		ASTNode current = child;
		while(current.getParent() != null) {
			if(current.getParent().equals(parent))
				return true;
			current = current.getParent();
		}
		return false;
	}

	private LocationInfo generateLocationInfo(CompilationUnit cu, String sourceFile, ASTNode node, CodeElementType codeElementType) {
		return new LocationInfo(cu, sourceFile, node, codeElementType);
	}
}

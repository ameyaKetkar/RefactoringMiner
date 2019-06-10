package org.refactoringminer.rm1;

import static gr.uom.java.xmi.DetailedTypeUtil.getDetailedType;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.refactoringminer.Models.DetailedTypeOuterClass.DetailedType;
import org.refactoringminer.Models.TypeWorldOuterClass.TypeWorld;
import org.refactoringminer.Models.TypeWorldOuterClass.TypeWorld.ClassWorld;
import org.refactoringminer.Models.TypeWorldOuterClass.TypeWorld.ClassWorld.Builder;
import org.refactoringminer.Models.TypeWorldOuterClass.TypeWorld.CompilationUnitWorld;
import org.refactoringminer.api.Churn;
import org.refactoringminer.api.GitHistoryRefactoringMiner;
import org.refactoringminer.api.GitService;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.api.RefactoringMinerTimedOutException;
import org.refactoringminer.api.RefactoringType;
import org.refactoringminer.util.GitServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.URL;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import gr.uom.java.xmi.UMLModel;
import gr.uom.java.xmi.UMLModelASTReader;

public class GitHistoryRefactoringMinerImpl implements GitHistoryRefactoringMiner {

	Logger logger = LoggerFactory.getLogger(GitHistoryRefactoringMinerImpl.class);
	private Set<RefactoringType> refactoringTypesToConsider = null;
	
	public GitHistoryRefactoringMinerImpl() {
		this.setRefactoringTypesToConsider(
			RefactoringType.RENAME_CLASS,
			RefactoringType.MOVE_CLASS,
			RefactoringType.MOVE_SOURCE_FOLDER,
			RefactoringType.RENAME_METHOD,
			RefactoringType.EXTRACT_OPERATION,
			RefactoringType.INLINE_OPERATION,
			RefactoringType.MOVE_OPERATION,
			RefactoringType.PULL_UP_OPERATION,
			RefactoringType.PUSH_DOWN_OPERATION,
			RefactoringType.MOVE_ATTRIBUTE,
			RefactoringType.MOVE_RENAME_ATTRIBUTE,
			RefactoringType.REPLACE_ATTRIBUTE,
			RefactoringType.PULL_UP_ATTRIBUTE,
			RefactoringType.PUSH_DOWN_ATTRIBUTE,
			RefactoringType.EXTRACT_INTERFACE,
			RefactoringType.EXTRACT_SUPERCLASS,
			RefactoringType.EXTRACT_SUBCLASS,
			RefactoringType.EXTRACT_CLASS,
			RefactoringType.EXTRACT_AND_MOVE_OPERATION,
			RefactoringType.MOVE_RENAME_CLASS,
			RefactoringType.RENAME_PACKAGE,
			RefactoringType.EXTRACT_VARIABLE,
			RefactoringType.INLINE_VARIABLE,
			RefactoringType.RENAME_VARIABLE,
			RefactoringType.RENAME_PARAMETER,
			RefactoringType.RENAME_ATTRIBUTE,
			RefactoringType.REPLACE_VARIABLE_WITH_ATTRIBUTE,
			RefactoringType.PARAMETERIZE_VARIABLE,
			RefactoringType.MERGE_VARIABLE,
			RefactoringType.MERGE_PARAMETER,
			RefactoringType.MERGE_ATTRIBUTE,
			RefactoringType.SPLIT_VARIABLE,
			RefactoringType.SPLIT_PARAMETER,
			RefactoringType.SPLIT_ATTRIBUTE,
			RefactoringType.CHANGE_RETURN_TYPE,
			RefactoringType.CHANGE_VARIABLE_TYPE,
			RefactoringType.CHANGE_PARAMETER_TYPE,
			RefactoringType.CHANGE_ATTRIBUTE_TYPE,
			RefactoringType.CLASS_DIFF_PROVIDER

		);
	}

	public void setRefactoringTypesToConsider(RefactoringType ... types) {
		this.refactoringTypesToConsider = new HashSet<>();
		for (RefactoringType type : types) {
			this.refactoringTypesToConsider.add(type);
		}
	}
	
	private void detect(GitService gitService, Repository repository, final RefactoringHandler handler, Iterator<RevCommit> i) {
		int commitsCount = 0;
		int errorCommitsCount = 0;
		int refactoringsCount = 0;

		File metadataFolder = repository.getDirectory();
		File projectFolder = metadataFolder.getParentFile();
		String projectName = projectFolder.getName();
		
		long time = System.currentTimeMillis();
		while (i.hasNext()) {
			RevCommit currentCommit = i.next();
			try {
				List<Refactoring> refactoringsAtRevision = detectRefactorings(gitService, repository, handler, projectFolder, currentCommit);
				refactoringsCount += refactoringsAtRevision.size();
				
			} catch (Exception e) {
				logger.warn(String.format("Ignored revision %s due to error", currentCommit.getId().getName()), e);
				handler.handleException(currentCommit.getId().getName(),e);
				errorCommitsCount++;
			}

			commitsCount++;
			long time2 = System.currentTimeMillis();
			if ((time2 - time) > 20000) {
				time = time2;
				logger.info(String.format("Processing %s [Commits: %d, Errors: %d, Refactorings: %d]", projectName, commitsCount, errorCommitsCount, refactoringsCount));
			}
		}

		handler.onFinish(refactoringsCount, commitsCount, errorCommitsCount);
		logger.info(String.format("Analyzed %s [Commits: %d, Errors: %d, Refactorings: %d]", projectName, commitsCount, errorCommitsCount, refactoringsCount));
	}

	protected List<Refactoring> detectRefactorings(GitService gitService, Repository repository, final RefactoringHandler handler, File projectFolder, RevCommit currentCommit) throws Exception {
		List<Refactoring> refactoringsAtRevision;
		String commitId = currentCommit.getId().getName();
		List<String> filePathsBefore = new ArrayList<String>();
		List<String> filePathsCurrent = new ArrayList<String>();
		Map<String, String> renamedFilesHint = new HashMap<String, String>();
		gitService.fileTreeDiff(repository, currentCommit, filePathsBefore, filePathsCurrent, renamedFilesHint);
		
		Set<String> repositoryDirectoriesBefore = new LinkedHashSet<String>();
		Set<String> repositoryDirectoriesCurrent = new LinkedHashSet<String>();
		Map<String, String> fileContentsBefore = new LinkedHashMap<String, String>();
		Map<String, String> fileContentsCurrent = new LinkedHashMap<String, String>();
		try (RevWalk walk = new RevWalk(repository)) {
			// If no java files changed, there is no refactoring. Also, if there are
			// only ADD's or only REMOVE's there is no refactoring
			if (!filePathsBefore.isEmpty() && !filePathsCurrent.isEmpty() && currentCommit.getParentCount() > 0) {
				RevCommit parentCommit = currentCommit.getParent(0);
				populateFileContents(repository, parentCommit, filePathsBefore, fileContentsBefore, repositoryDirectoriesBefore);
				UMLModel parentUMLModel = createModel(projectFolder, fileContentsBefore, repositoryDirectoriesBefore);

				populateFileContents(repository, currentCommit, filePathsCurrent, fileContentsCurrent, repositoryDirectoriesCurrent);
				UMLModel currentUMLModel = createModel(projectFolder, fileContentsCurrent, repositoryDirectoriesCurrent);
				
				refactoringsAtRevision = parentUMLModel.diff(currentUMLModel, renamedFilesHint).getRefactorings();
				refactoringsAtRevision = filter(refactoringsAtRevision);
			} else {
				//logger.info(String.format("Ignored revision %s with no changes in java files", commitId));
				refactoringsAtRevision = Collections.emptyList();
			}
			handler.handle(commitId, refactoringsAtRevision);
			handler.handle(currentCommit, refactoringsAtRevision);
			
			walk.dispose();
		}
		return refactoringsAtRevision;
	}

	private void populateFileContents(Repository repository, RevCommit commit,
			List<String> filePaths, Map<String, String> fileContents, Set<String> repositoryDirectories) throws Exception {
		logger.info("Processing {} {} ...", repository.getDirectory().getParent().toString(), commit.getName());
		RevTree parentTree = commit.getTree();
		try (TreeWalk treeWalk = new TreeWalk(repository)) {
			treeWalk.addTree(parentTree);
			treeWalk.setRecursive(true);
			while (treeWalk.next()) {
				String pathString = treeWalk.getPathString();
				if(filePaths.contains(pathString)) {
					ObjectId objectId = treeWalk.getObjectId(0);
					ObjectLoader loader = repository.open(objectId);
					StringWriter writer = new StringWriter();
					IOUtils.copy(loader.openStream(), writer);
					fileContents.put(pathString, writer.toString());
				}
				if(pathString.endsWith(".java")) {
					repositoryDirectories.add(pathString.substring(0, pathString.lastIndexOf("/")));
				}
			}
		}
	}

	protected List<Refactoring> detectRefactorings(final RefactoringHandler handler, File projectFolder, String cloneURL, String currentCommitId) {
		List<Refactoring> refactoringsAtRevision = Collections.emptyList();
		try {
			List<String> filesBefore = new ArrayList<String>();
			List<String> filesCurrent = new ArrayList<String>();
			Map<String, String> renamedFilesHint = new HashMap<String, String>();
			String parentCommitId = populateWithGitHubAPI(cloneURL, currentCommitId, filesBefore, filesCurrent, renamedFilesHint);
			File currentFolder = new File(projectFolder.getParentFile(), projectFolder.getName() + "-" + currentCommitId);
			File parentFolder = new File(projectFolder.getParentFile(), projectFolder.getName() + "-" + parentCommitId);
			if (!currentFolder.exists()) {	
				downloadAndExtractZipFile(projectFolder, cloneURL, currentCommitId);
			}
			if (!parentFolder.exists()) {	
				downloadAndExtractZipFile(projectFolder, cloneURL, parentCommitId);
			}
			if (currentFolder.exists() && parentFolder.exists()) {
				UMLModel currentUMLModel = createModel(currentFolder, filesCurrent, repositoryDirectories(currentFolder));
				UMLModel parentUMLModel = createModel(parentFolder, filesBefore, repositoryDirectories(parentFolder));
				// Diff between currentModel e parentModel
				refactoringsAtRevision = parentUMLModel.diff(currentUMLModel, renamedFilesHint).getRefactorings();
				refactoringsAtRevision = filter(refactoringsAtRevision);
			}
			else {
				logger.warn(String.format("Folder %s not found", currentFolder.getPath()));
			}
		} catch (Exception e) {
			logger.warn(String.format("Ignored revision %s due to error", currentCommitId), e);
			handler.handleException(currentCommitId, e);
		}
		handler.handle(currentCommitId, refactoringsAtRevision);

		return refactoringsAtRevision;
	}

	private Set<String> repositoryDirectories(File folder) {
		final String systemFileSeparator = Matcher.quoteReplacement(File.separator);
		Set<String> repositoryDirectories = new LinkedHashSet<String>();
		Collection<File> files = FileUtils.listFiles(folder, null, true);
		for(File file : files) {
			String path = file.getPath();
			String relativePath = path.substring(folder.getPath().length()+1, path.length()).replaceAll(systemFileSeparator, "/");
			if(relativePath.endsWith(".java")) {
				repositoryDirectories.add(relativePath.substring(0, relativePath.lastIndexOf("/")));
			}
		}
		return repositoryDirectories;
	}

	private void downloadAndExtractZipFile(File projectFolder, String cloneURL, String commitId)
			throws IOException {
		String downloadLink = cloneURL.substring(0, cloneURL.indexOf(".git")) + "/archive/" + commitId + ".zip";
		File destinationFile = new File(projectFolder.getParentFile(), projectFolder.getName() + "-" + commitId + ".zip");
		logger.info(String.format("Downloading archive %s", downloadLink));
		FileUtils.copyURLToFile(new URL(downloadLink), destinationFile);
		logger.info(String.format("Unzipping archive %s", downloadLink));
		java.util.zip.ZipFile zipFile = new ZipFile(destinationFile);
		try {
			Enumeration<? extends ZipEntry> entries = zipFile.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				File entryDestination = new File(projectFolder.getParentFile(),  entry.getName());
				if (entry.isDirectory()) {
					entryDestination.mkdirs();
				} else {
					entryDestination.getParentFile().mkdirs();
					InputStream in = zipFile.getInputStream(entry);
					OutputStream out = new FileOutputStream(entryDestination);
					IOUtils.copy(in, out);
					IOUtils.closeQuietly(in);
					out.close();
				}
			}
		} finally {
			zipFile.close();
		}
	}

	private String populateWithGitHubAPI(String cloneURL, String currentCommitId,
			List<String> filesBefore, List<String> filesCurrent, Map<String, String> renamedFilesHint) throws IOException {
		Properties prop = new Properties();
		InputStream input = new FileInputStream("github-credentials.properties");
		prop.load(input);
		String username = prop.getProperty("username");
		String password = prop.getProperty("password");
		String parentCommitId = null;
		GitHub gitHub = null;
		if (username != null && password != null) {
			gitHub = GitHub.connectUsingPassword(username, password);
		}
		else {
			gitHub = GitHub.connect();
		}
		//https://github.com/ is 19 chars
		String repoName = cloneURL.substring(19, cloneURL.indexOf(".git"));
		GHRepository repository = gitHub.getRepository(repoName);
		GHCommit commit = repository.getCommit(currentCommitId);
		parentCommitId = commit.getParents().get(0).getSHA1();
		List<GHCommit.File> commitFiles = commit.getFiles();
		for (GHCommit.File commitFile : commitFiles) {
			if (commitFile.getFileName().endsWith(".java")) {
				if (commitFile.getStatus().equals("modified")) {
					filesBefore.add(commitFile.getFileName());
					filesCurrent.add(commitFile.getFileName());
				}
				else if (commitFile.getStatus().equals("added")) {
					filesCurrent.add(commitFile.getFileName());
				}
				else if (commitFile.getStatus().equals("removed")) {
					filesBefore.add(commitFile.getFileName());
				}
				else if (commitFile.getStatus().equals("renamed")) {
					filesBefore.add(commitFile.getPreviousFilename());
					filesCurrent.add(commitFile.getFileName());
					renamedFilesHint.put(commitFile.getPreviousFilename(), commitFile.getFileName());
				}
			}
		}
		return parentCommitId;
	}

	protected List<Refactoring> filter(List<Refactoring> refactoringsAtRevision) {
		if (this.refactoringTypesToConsider == null) {
			return refactoringsAtRevision;
		}
		List<Refactoring> filteredList = new ArrayList<Refactoring>();
		for (Refactoring ref : refactoringsAtRevision) {
			if (this.refactoringTypesToConsider.contains(ref.getRefactoringType())) {
				filteredList.add(ref);
			}
		}
		return filteredList;
	}
	
	@Override
	public void detectAll(Repository repository, String branch, final RefactoringHandler handler) throws Exception {
		GitService gitService = new GitServiceImpl() {
			@Override
			public boolean isCommitAnalyzed(String sha1) {
				return handler.skipCommit(sha1);
			}
		};
		RevWalk walk = gitService.createAllRevsWalk(repository, branch);
		try {
			detect(gitService, repository, handler, walk.iterator());
		} finally {
			walk.dispose();
		}
	}

	@Override
	public void fetchAndDetectNew(Repository repository, final RefactoringHandler handler) throws Exception {
		GitService gitService = new GitServiceImpl() {
			@Override
			public boolean isCommitAnalyzed(String sha1) {
				return handler.skipCommit(sha1);
			}
		};
		RevWalk walk = gitService.fetchAndCreateNewRevsWalk(repository);
		try {
			detect(gitService, repository, handler, walk.iterator());
		} finally {
			walk.dispose();
		}
	}

	protected UMLModel createModel(File projectFolder, Map<String, String> fileContents, Set<String> repositoryDirectories) throws Exception {
		return new UMLModelASTReader(projectFolder, fileContents, repositoryDirectories).getUmlModel();
	}

	protected UMLModel createModel(File projectFolder, List<String> filePaths, Set<String> repositoryDirectories) throws Exception {
		return new UMLModelASTReader(projectFolder, filePaths, repositoryDirectories).getUmlModel();
	}

	@Override
	public void detectAtCommit(Repository repository, String cloneURL, String commitId, RefactoringHandler handler) {
		File metadataFolder = repository.getDirectory();
		File projectFolder = metadataFolder.getParentFile();
		GitService gitService = new GitServiceImpl();
		RevWalk walk = new RevWalk(repository);
		try {
			RevCommit commit = walk.parseCommit(repository.resolve(commitId));
			if (commit.getParentCount() > 0) {
				walk.parseCommit(commit.getParent(0));
				this.detectRefactorings(gitService, repository, handler, projectFolder, commit);
			}
			else {
				logger.warn(String.format("Ignored revision %s because it has no parent", commitId));
			}
		} catch (MissingObjectException moe) {
			this.detectRefactorings(handler, projectFolder, cloneURL, commitId);
		} catch (RefactoringMinerTimedOutException e) {
			logger.warn(String.format("Ignored revision %s due to timeout", commitId), e);
		} catch (Exception e) {
			logger.warn(String.format("Ignored revision %s due to error", commitId), e);
			handler.handleException(commitId, e);
		} finally {
			walk.close();
			walk.dispose();
		}
	}

	public void detectAtCommit(Repository repository, String cloneURL, String commitId, RefactoringHandler handler, int timeout) {
		ExecutorService service = Executors.newSingleThreadExecutor();
		Future<?> f = null;
		try {
			Runnable r = () -> detectAtCommit(repository, cloneURL, commitId, handler);
			f = service.submit(r);
			f.get(timeout, TimeUnit.SECONDS);
		} catch (TimeoutException e) {
			f.cancel(true);
		} catch (ExecutionException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			service.shutdown();
		}
	}

	@Override
	public String getConfigId() {
	    return "RM1";
	}

	@Override
	public void detectBetweenTags(Repository repository, String startTag, String endTag, RefactoringHandler handler)
			throws Exception {
		GitService gitService = new GitServiceImpl() {
			@Override
			public boolean isCommitAnalyzed(String sha1) {
				return handler.skipCommit(sha1);
			}
		};
		
		Iterable<RevCommit> walk = gitService.createRevsWalkBetweenTags(repository, startTag, endTag);
		detect(gitService, repository, handler, walk.iterator());
	}

	@Override
	public void detectBetweenCommits(Repository repository, String startCommitId, String endCommitId,
			RefactoringHandler handler) throws Exception {
		GitService gitService = new GitServiceImpl() {
			@Override
			public boolean isCommitAnalyzed(String sha1) {
				return handler.skipCommit(sha1);
			}
		};
		
		Iterable<RevCommit> walk = gitService.createRevsWalkBetweenCommits(repository, startCommitId, endCommitId);
		detect(gitService, repository, handler, walk.iterator());
	}

	@Override
	public Churn churnAtCommit(Repository repository, String commitId, RefactoringHandler handler) {
		GitService gitService = new GitServiceImpl();
		RevWalk walk = new RevWalk(repository);
		try {
			RevCommit commit = walk.parseCommit(repository.resolve(commitId));
			if (commit.getParentCount() > 0) {
				walk.parseCommit(commit.getParent(0));
				return gitService.churn(repository, commit);
			}
			else {
				logger.warn(String.format("Ignored revision %s because it has no parent", commitId));
			}
		} catch (MissingObjectException moe) {
			logger.warn(String.format("Ignored revision %s due to missing commit", commitId), moe);
		} catch (Exception e) {
			logger.warn(String.format("Ignored revision %s due to error", commitId), e);
			handler.handleException(commitId, e);
		} finally {
			walk.close();
			walk.dispose();
		}
		return null;
	}

	public SimpleImmutableEntry<Set<String>, Set<String>> getBeforeAfterFileStruct(GitService gitService, Repository repository, RevCommit currentCommit) throws Exception {
		List<String> filePathsBefore = new ArrayList<>();
		List<String> filePathsCurrent = new ArrayList<>();
		Map<String, String> renamedFilesHint = new HashMap<>();
		gitService.fileTreeDiff(repository, currentCommit, filePathsBefore, filePathsCurrent, renamedFilesHint);

		try (RevWalk walk = new RevWalk(repository)) {
			if (!filePathsBefore.isEmpty() && !filePathsCurrent.isEmpty() && currentCommit.getParentCount() > 0) {
				RevCommit parentCommit = currentCommit.getParent(0);
				return new SimpleImmutableEntry<>(getB4AfFileStruct(repository, parentCommit), getB4AfFileStruct(repository, currentCommit));
			}
			walk.dispose();
		}catch (Exception e){
			e.printStackTrace();
		}
		return new SimpleImmutableEntry<>(new HashSet<>(), new HashSet<>());
	}

	public static  Optional<TypeWorld> getTypeWorld(GitService gitService, Repository repository, RevCommit currentCommit, String projectName)  {

		try {
			List<String> filePathsBefore = new ArrayList<>();
			List<String> filePathsCurrent = new ArrayList<>();
			Map<String, String> renamedFilesHint = new HashMap<>();
			gitService.fileTreeDiff(repository, currentCommit, filePathsBefore, filePathsCurrent, renamedFilesHint);

			try (RevWalk walk = new RevWalk(repository)) {
				if (!filePathsCurrent.isEmpty() && currentCommit.getParentCount() > 0) {
					return Optional.ofNullable(createTypeWorld(repository, currentCommit, projectName));
				}
				walk.dispose();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}catch (Exception e){
			e.printStackTrace();
		}
		return Optional.empty();
	}



	Set<String> getB4AfFileStruct(Repository repository, RevCommit commit) {
		Set<String> repositoryDirectories = new HashSet<>();
		RevTree parentTree = commit.getTree();
		try (TreeWalk treeWalk = new TreeWalk(repository)) {
			treeWalk.addTree(parentTree);
			treeWalk.setRecursive(true);
			while (treeWalk.next()) {
				String pathString = treeWalk.getPathString();
				if(pathString.endsWith(".java")) {
					repositoryDirectories.add(pathString.replace(".java",""));
				}
			}
		}
		catch (Exception e){
			e.printStackTrace();
		}
		return repositoryDirectories.stream().map(x -> x.replace("/",".")).collect(toSet());
	}


	public static TypeWorld createTypeWorld(Repository repository, RevCommit commit, String projectName) {
		Set<String> repositoryDirectories = new HashSet<>();
		TypeWorld.Builder tw = TypeWorld.newBuilder().addAllAllFiles(repositoryDirectories)
				.setCommit(commit.getId().getName())
				.setProject(projectName);
		RevTree parentTree = commit.getTree();
		try (TreeWalk treeWalk = new TreeWalk(repository)) {
			treeWalk.addTree(parentTree);
			treeWalk.setRecursive(true);
			while (treeWalk.next()) {
				String pathString = treeWalk.getPathString();
				if(pathString.endsWith(".java")) {
					String fileName = pathString.replace(".java", "");
					tw.putCus(fileName, getCompilationUnitWorld(getCuFor(getFileContent(repository, treeWalk)), fileName));
					repositoryDirectories.add(fileName);
				}
			}
		}
		catch (Exception e){
			e.printStackTrace();
		}
		return tw.build();
	}


	public static Map<String,CompilationUnitWorld> getCompilationUnitWorldFor(Repository repository, RevCommit commit, List<String> path){
		final RevTree parentTree = commit.getTree();
		Map<String, CompilationUnitWorld> cus = new HashMap<>();
		try (TreeWalk treeWalk = new TreeWalk(repository)) {
			treeWalk.addTree(parentTree);
			treeWalk.setRecursive(true);
			while (treeWalk.next()) {
				String pathString = treeWalk.getPathString();
				if(pathString.endsWith(".java") &&
						path.stream().anyMatch(pathString::contains)) {
					final String fileName = pathString.replace(".java", "");
					cus.put(fileName,getCompilationUnitWorld(getCuFor(getFileContent(repository, treeWalk)), fileName));
				}
			}
		}catch(Exception e){
			e.printStackTrace();
		}
		return cus;
	}

	public static CompilationUnitWorld getCompilationUnitWorld(CompilationUnit cu, String fileName){
		CompilationUnitWorld.Builder cuw = CompilationUnitWorld.newBuilder();
		List<ImportDeclaration> imports = cu.imports();
		cuw.addAllImportsStatements(imports.stream().filter(x->!x.isOnDemand()).map(x->x.getName().getFullyQualifiedName()).collect(toList()));
		cuw.addAllImportsOnDemand(imports.stream().filter(x->x.isOnDemand()).map(x->x.getName().getFullyQualifiedName()).collect(toList()));
		cuw.setPackage(cu.getPackage().getName().getFullyQualifiedName());
		cuw.setFile(fileName);
		cuw.addAllUsedTypes(getAllDetailedTypesInCu(cu));
		final List<AbstractTypeDeclaration> typeDeclarations = cu.types();
		for(AbstractTypeDeclaration a : typeDeclarations){
			if(a instanceof TypeDeclaration){
				cuw.addClasses(getClassWorld((TypeDeclaration) a));
			}
			if (a instanceof EnumDeclaration){
				cuw.addClasses(getClassWorld((EnumDeclaration) a));
			}
		}
		return cuw.build();

	}


	public static Set<DetailedType> getAllDetailedTypesInCu(CompilationUnit cu){

		class GetAllUsedVariableTypes extends ASTVisitor {
			Set<DetailedType> dt = new HashSet<>();
			@Override
			public boolean visit(VariableDeclarationExpression node) {
				dt.add(getDetailedType(node.getType()));
				return true;
			}
		}
		GetAllUsedVariableTypes x = new GetAllUsedVariableTypes();
		cu.accept(x);
		return x.dt;
	}

	static ClassWorld getClassWorld(EnumDeclaration ed){
		return ClassWorld.newBuilder().setName(ed.getName().getIdentifier()).build();
	}

	static ClassWorld getClassWorld(TypeDeclaration td){
		Builder clsBldr = ClassWorld.newBuilder().setName(td.getName().getIdentifier());
		List<TypeParameter> tps = new ArrayList<>();
		if(!td.typeParameters().isEmpty())
			tps.addAll(td.typeParameters());

		clsBldr.addAllTypeParameters(tps.stream()
				.map(x->x.getName().getIdentifier())
				.collect(toList()));

		Optional.ofNullable(td.getSuperclassType())
				.ifPresent(t -> clsBldr.addSuperTypes(getDetailedType(t)));

		if(!td.superInterfaceTypes().isEmpty()){
			List<Type> interfaces = td.superInterfaceTypes();
			clsBldr.addAllSuperTypes(interfaces.stream().map(x->getDetailedType(x)).collect(toList()));
		}

		clsBldr.addAllNestedClasses(Arrays.stream(td.getTypes()).map(x->getClassWorld(x)).collect(toList()));

		class NestedEmnumVisitor extends ASTVisitor{
			public List<EnumDeclaration> localEnums = new ArrayList<>();
			@Override
			public boolean visit(EnumDeclaration node) {
				localEnums.add(node);
				return super.visit(node);
			}
		}

		NestedEmnumVisitor nev = new NestedEmnumVisitor();
		td.accept(nev);
		if(!nev.localEnums.isEmpty()){
			clsBldr.addAllNestedClasses(nev.localEnums.stream().map(x->getClassWorld(x)).collect(toList()));
		}

		class localClassVisitor extends ASTVisitor{
			public List<TypeDeclaration> localClasses = new ArrayList<>();
			@Override
			public boolean visit(TypeDeclaration node) {
				localClasses.add(node);
				return super.visit(node);
			}
		}

		for(MethodDeclaration md : td.getMethods()){
			localClassVisitor lc = new localClassVisitor();
			if(md.getBody()!=null) {
				md.getBody().accept(lc);
				if (!lc.localClasses.isEmpty())
					clsBldr.addAllNestedClasses(lc.localClasses.stream().map(x -> getClassWorld(x)).collect(toList()));
			}
		}


		if(td.getFields().length > 0){
			clsBldr.addAllComposeTypes( Arrays.stream(td.getFields()).map(x->getDetailedType(x.getType())).collect(toList()));
		}

		if(td.getMethods().length > 0){
			clsBldr.addAllComposeTypes(Arrays.stream(td.getMethods())
					.map(x->x.getReturnType2())
					.filter(Objects::nonNull)
					.map(x->getDetailedType(x))
					.collect(toList()));
		}


		return clsBldr.build();

	}



	private static String getFileContent(Repository repository, TreeWalk treeWalk) throws IOException {
		ObjectId objectId = treeWalk.getObjectId(0);
		ObjectLoader loader = repository.open(objectId);
		StringWriter writer = new StringWriter();
		IOUtils.copy(loader.openStream(), writer);
		return Optional.ofNullable(writer.toString()).orElse("");
	}


	public static CompilationUnit getCuFor(String content){
		ASTParser parser = ASTParser.newParser(AST.JLS11);
		Map<String, String> options = JavaCore.getOptions();
		options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_1_8);
		options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
		options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_8);
		parser.setCompilerOptions(options);
		parser.setResolveBindings(false);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setStatementsRecovery(true);
		parser.setSource(content.toCharArray());
		return  (CompilationUnit)parser.createAST(null);
	}
}

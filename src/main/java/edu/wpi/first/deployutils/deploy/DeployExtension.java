package edu.wpi.first.deployutils.deploy;

import java.util.concurrent.Callable;

import javax.inject.Inject;

import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.diagnostics.TaskReportTask;

import edu.wpi.first.deployutils.deploy.artifact.ActionArtifact;
import edu.wpi.first.deployutils.deploy.artifact.Artifact;
import edu.wpi.first.deployutils.deploy.artifact.CacheableArtifact;
import edu.wpi.first.deployutils.deploy.artifact.CommandArtifact;
import edu.wpi.first.deployutils.deploy.artifact.FileArtifact;
import edu.wpi.first.deployutils.deploy.artifact.FileCollectionArtifact;
import edu.wpi.first.deployutils.deploy.artifact.FileTreeArtifact;
import edu.wpi.first.deployutils.deploy.artifact.JavaArtifact;
import edu.wpi.first.deployutils.deploy.artifact.MavenArtifact;
import edu.wpi.first.deployutils.deploy.artifact.MultiCommandArtifact;
import edu.wpi.first.deployutils.deploy.artifact.NativeExecutableArtifact;
import edu.wpi.first.deployutils.deploy.cache.CacheMethod;
import edu.wpi.first.deployutils.deploy.cache.Md5FileCacheMethod;
import edu.wpi.first.deployutils.deploy.cache.Md5SumCacheMethod;
import edu.wpi.first.deployutils.deploy.target.RemoteTarget;
import edu.wpi.first.deployutils.deploy.target.location.DeployLocation;
import edu.wpi.first.deployutils.deploy.target.location.SshDeployLocation;

public class DeployExtension {

    private final TaskProvider<Task> deployTask;
    private final ExtensiblePolymorphicDomainObjectContainer<RemoteTarget> targets;
    private final ExtensiblePolymorphicDomainObjectContainer<CacheMethod> cache;

    public ExtensiblePolymorphicDomainObjectContainer<RemoteTarget> getTargets() {
        return targets;
    }

    public ExtensiblePolymorphicDomainObjectContainer<CacheMethod> getCache() {
        return cache;
    }

    public TaskProvider<Task> getDeployTask() {
        return deployTask;
    }

    private void configureTargetTypes(RemoteTarget target) {
        ObjectFactory objects = target.getProject().getObjects();
        ExtensiblePolymorphicDomainObjectContainer<DeployLocation> locations = target.getLocations();
        ExtensiblePolymorphicDomainObjectContainer<Artifact> artifacts = target.getArtifacts();

        NamedObjectFactory.registerType(NativeExecutableArtifact.class, artifacts, target, objects);
        NamedObjectFactory.registerType(FileArtifact.class, artifacts, target, objects);
        NamedObjectFactory.registerType(JavaArtifact.class, artifacts, target, objects);
        NamedObjectFactory.registerType(ActionArtifact.class, artifacts, target, objects);
        NamedObjectFactory.registerType(FileCollectionArtifact.class, artifacts, target, objects);
        NamedObjectFactory.registerType(FileTreeArtifact.class, artifacts, target, objects);
        NamedObjectFactory.registerType(MavenArtifact.class, artifacts, target, objects);
        NamedObjectFactory.registerType(MultiCommandArtifact.class, artifacts, target, objects);
        NamedObjectFactory.registerType(CommandArtifact.class, artifacts, target, objects);

        NamedObjectFactory.registerType(SshDeployLocation.class, locations, target, objects);
    }

    @Inject
    public DeployExtension(Project project, ObjectFactory objects) {

        targets = objects.polymorphicDomainObjectContainer(RemoteTarget.class);
        cache = objects.polymorphicDomainObjectContainer(CacheMethod.class);

        cache.registerFactory(Md5SumCacheMethod.class, name -> {
            return objects.newInstance(Md5SumCacheMethod.class, name);
        });

        cache.registerFactory(Md5FileCacheMethod.class, name -> {
            return objects.newInstance(Md5FileCacheMethod.class, name);
        });

        cache.register("md5sum", Md5SumCacheMethod.class);

        targets.registerFactory(RemoteTarget.class, name -> {
            return objects.newInstance(RemoteTarget.class, name, project, this);
        });

        // Empty all forces all registered items to be instantly resolved.
        // Without this, any registered tasks will crash when deploy is called
        // Also resolve all inner artifacts for same reason
        targets.all(x -> {
            configureTargetTypes(x);
            x.getArtifacts().all(y -> {
                if (y instanceof CacheableArtifact) {
                    Property<CacheMethod> cm = ((CacheableArtifact)y).getCacheMethod();
                    if (!cm.isPresent()) {
                        cm.set(cache.getByName("md5sum"));
                    }
                }
            });
        });

        deployTask = project.getTasks().register("deploy", task -> {
            task.setGroup("DeployUtils");
            task.setDescription("Deploy all artifacts on all targets");
            targets.all(x -> {
                task.dependsOn(x.getDeployTask());
            });
        });

        project.getTasks().withType(TaskReportTask.class).configureEach(t -> {
            Callable<Task> enumTask = () -> {
                targets.all(x -> x.getArtifacts().all(y -> {}));
                return null;
            };
            t.dependsOn(enumTask);
        });
    }
}

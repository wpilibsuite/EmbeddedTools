package jaci.gradle.deploy.artifact

import groovy.transform.CompileStatic
import org.gradle.api.Action
import jaci.gradle.deploy.context.DeployContext
import org.gradle.api.DomainObjectSet
import org.gradle.api.Project
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.api.tasks.TaskCollection

import javax.inject.Inject

@CompileStatic
abstract class AbstractArtifact implements Artifact {
    private final String name
    private final Project project

    private DomainObjectSet<Object> dependencies = new DefaultDomainObjectSet<>(Object)
    private DomainObjectSet<Object> targets = new DefaultDomainObjectSet<>(Object)

    private disabled = false

    @Inject
    AbstractArtifact(String name, Project project) {
        this.name = name
        this.project = project
    }

    Project getProject() {
        return project
    }

    String getName() {
        return name
    }

    DomainObjectSet<Object> getDependencies() {
        return dependencies
    }

    DomainObjectSet<Object> getTargets() {
        return targets
    }

    void dependsOn(Object... paths) {
        for (Object val : paths)
            dependencies.add(val)
    }

    TaskCollection<ArtifactDeployTask> getTasks() {
        return project.tasks.withType(ArtifactDeployTask).matching { ArtifactDeployTask t ->
            t.artifact == this
        }
    }

    // Groovy generates get/set
    String directory = null
    // Need the WrappedArrayList to overload operator <<
    List<Action<DeployContext>> predeploy  = new WrappedArrayList()
    List<Action<DeployContext>> postdeploy = new WrappedArrayList()
    Action<DeployContext> onlyIf           = null

    // Must declare both, as groovy's implicit properties
    // get disabled with an explicit implementation
    void setOnlyIf(Closure closure) {
        onlyIf = new ActionWrapper(closure)
    }

    void setOnlyIf(Action<DeployContext> action) {
        onlyIf = action
    }

    void setDisabled() {
        setDisabled(true)
    }

    void setDisabled(boolean state) {
        this.disabled = state
    }

    boolean isDisabled() {
        return disabled
    }

    boolean isEnabled(DeployContext ctx) {
        return disabled ? false :
                onlyIf == null ? true :
                        (onlyIf.execute(ctx) || ctx?.deployLocation?.target?.isDry())
    }

    @Override
    String toString() {
        return "${this.class.simpleName}[${this.name}]".toString()
    }
}

package com.lingmaforge.backend.common.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class CommonModelSerializationTest {

    @Test
    void commonModelsAreSerializableForLangGraphState() {
        serializableTypes().forEach(type -> assertThat(Serializable.class.isAssignableFrom(type))
                .as("%s should implement Serializable", type.getName())
                .isTrue());
    }

    @Test
    void commonModelsDeclareSerialVersionUid() {
        serializableTypes().forEach(CommonModelSerializationTest::assertSerialVersionUid);
    }

    private static Stream<Class<?>> serializableTypes() {
        return Stream.of(
                BuildResult.class,
                BuildStatus.class,
                CreateGenerationRequest.class,
                CreateProjectRequest.class,
                FileModification.class,
                FileNode.class,
                FilePlan.class,
                GeneratedFile.class,
                GenerationTaskResponse.class,
                GenerationTaskStatusResponse.class,
                GraphNodeResponse.class,
                IterateRequest.class,
                Patch.class,
                PlanResult.class,
                ProjectContext.class,
                ProjectResponse.class,
                RequirementSpec.class,
                RequirementSpec.PageSpec.class,
                RequirementSpec.ApiSpec.class,
                RequirementSpec.StyleSpec.class,
                SandboxInfo.class,
                UpdateFileRequest.class,
                UpdateProjectRequest.class);
    }

    private static void assertSerialVersionUid(Class<?> type) {
        try {
            Field field = type.getDeclaredField("serialVersionUID");
            int modifiers = field.getModifiers();

            assertThat(field.getType())
                    .as("%s serialVersionUID type", type.getName())
                    .isEqualTo(long.class);
            assertThat(Modifier.isPrivate(modifiers))
                    .as("%s serialVersionUID should be private", type.getName())
                    .isTrue();
            assertThat(Modifier.isStatic(modifiers))
                    .as("%s serialVersionUID should be static", type.getName())
                    .isTrue();
            assertThat(Modifier.isFinal(modifiers))
                    .as("%s serialVersionUID should be final", type.getName())
                    .isTrue();
        } catch (NoSuchFieldException exception) {
            throw new AssertionError(type.getName() + " should declare serialVersionUID", exception);
        }
    }
}

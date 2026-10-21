package com.gtnewhorizons.modularnuclear.client.renderer;

import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

/**
 * Lightweight GLSL shader that desaturates all fragment colors to grayscale
 * using standard ITU-R Rec. 709 / sRGB luminance weights.
 * Used by reactor grid GUI to render component icons in pure grayscale under heatmaps.
 */
public final class GrayscaleShader {

    private static int program = 0;
    private static boolean initialized = false;
    private static boolean supported = true;

    private GrayscaleShader() {}

    private static void init() {
        if (initialized) return;
        initialized = true;

        try {
            if (!Display.isCreated()) {
                supported = false;
                return;
            }
            if (!GLContext.getCapabilities().OpenGL20) {
                supported = false;
                return;
            }

            int vertShader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
            int fragShader = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
            if (vertShader == 0 || fragShader == 0) {
                supported = false;
                return;
            }

            String vertSource = "#version 120\n" + "void main() {\n"
                + "    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n"
                + "    gl_TexCoord[0] = gl_TextureMatrix[0] * gl_MultiTexCoord0;\n"
                + "    gl_FrontColor = gl_Color;\n"
                + "}\n";

            String fragSource = "#version 120\n" + "uniform sampler2D u_texture;\n"
                + "void main() {\n"
                + "    vec4 texColor = texture2D(u_texture, gl_TexCoord[0].st);\n"
                + "    vec4 color = texColor * gl_Color;\n"
                + "    float gray = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));\n"
                + "    gl_FragColor = vec4(gray, gray, gray, color.a);\n"
                + "}\n";

            GL20.glShaderSource(vertShader, vertSource);
            GL20.glCompileShader(vertShader);
            if (GL20.glGetShaderi(vertShader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
                supported = false;
                GL20.glDeleteShader(vertShader);
                GL20.glDeleteShader(fragShader);
                return;
            }

            GL20.glShaderSource(fragShader, fragSource);
            GL20.glCompileShader(fragShader);
            if (GL20.glGetShaderi(fragShader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
                supported = false;
                GL20.glDeleteShader(vertShader);
                GL20.glDeleteShader(fragShader);
                return;
            }

            program = GL20.glCreateProgram();
            if (program == 0) {
                supported = false;
                GL20.glDeleteShader(vertShader);
                GL20.glDeleteShader(fragShader);
                return;
            }

            GL20.glAttachShader(program, vertShader);
            GL20.glAttachShader(program, fragShader);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                supported = false;
                GL20.glDeleteProgram(program);
                GL20.glDeleteShader(vertShader);
                GL20.glDeleteShader(fragShader);
                program = 0;
                return;
            }

            // Cleanup attached shader objects after linking
            GL20.glDeleteShader(vertShader);
            GL20.glDeleteShader(fragShader);

            int texLoc = GL20.glGetUniformLocation(program, "u_texture");
            if (texLoc >= 0) {
                GL20.glUseProgram(program);
                GL20.glUniform1i(texLoc, 0);
                GL20.glUseProgram(0);
            }
        } catch (Throwable t) {
            supported = false;
            program = 0;
        }
    }

    private static int previousProgram = 0;

    /**
     * Activates the grayscale desaturation shader.
     * 
     * @return true if the shader was successfully bound, false if shaders are unsupported or failed to compile.
     */
    public static boolean begin() {
        init();
        if (!supported || program == 0) {
            return false;
        }
        previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL20.glUseProgram(program);
        return true;
    }

    /**
     * Deactivates the grayscale shader and restores previous shader program.
     */
    public static void end() {
        if (supported && program != 0) {
            GL20.glUseProgram(previousProgram);
            previousProgram = 0;
        }
    }
}

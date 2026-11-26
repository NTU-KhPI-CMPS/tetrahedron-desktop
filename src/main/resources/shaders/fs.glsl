#version 300 es
precision highp float;

const vec3 edgeColor = vec3(0.918, 0.956, 1.0);

uniform mat4 viewMatrix;
uniform mat4 projMatrix;
uniform mat4 modelMatrix;

uniform int showElementMesh;
uniform int showLight;
uniform int coloredInSelectedColor;
// If this is set to 1 shader will render depth value packed in 4 channels (RGBA) instead of a color
uniform int isDepthReading;
uniform vec3 modelColor;

in vec3 fragmentColor;
in vec3 barycentricCoords;
in vec3 triangleNormal;
in float depth;

// Packs a float value into a 4-component vector of 8-bit values.
// This allows storing high precision data in a standard RGBA8 texture.
vec4 packDepth(const in float depth) {
    // These constants are used to shift the bits of the float
    const vec4 bitShift = vec4(1.0, 255.0, 255.0 * 255.0, 255.0 * 255.0 * 255.0);
    const vec4 bitMask  = vec4(1.0/255.0, 1.0/255.0, 1.0/255.0, 0.0);

    // Multiply the depth by the bit shifts, then get the fractional part.
    // This isolates the bits for each channel.
    vec4 res = fract(depth * bitShift);

    // The `res -= res.xxyz * bitMask` step is crucial. It subtracts the
    // "carried-over" part from the higher-order components.
    // For example, res.y (the green channel) contains the value for its
    // byte, but also a fractional part of what's in res.x. This removes it.
    res -= res.xxyz * bitMask;

    return res;
}

out vec4 color;

vec3 calculateLighting(vec3 color) {

    vec3 lightDirection = normalize(vec3(0.0, 0.5, 1));

    vec3 normal = normalize((modelMatrix * vec4(triangleNormal, 0.0)).xyz);
    vec3 viewLightDirection = normalize((viewMatrix * vec4(lightDirection, 0.0)).xyz);

    float diff = abs(dot(normal, viewLightDirection));
    vec3 finalColor = diff * color;

    return finalColor;
}

void main(void) {
  vec3 pixelColor;

  // define face color (default one or provided in vertex info)
  if (coloredInSelectedColor == 0) {
    pixelColor = fragmentColor;
  } else {
    pixelColor = modelColor;
  }

  // show edges if enabled
  if (showElementMesh == 1) {
    vec3 d = fwidth(barycentricCoords);
    vec3 f = smoothstep(vec3(0.0), d, barycentricCoords);
    float factor = min(min(f.x, f.y), f.z);

    pixelColor = mix(edgeColor, pixelColor, factor);
  }

  // show light if enabled
  if (showLight == 1) {
    pixelColor = calculateLighting(pixelColor);
  }

  if (isDepthReading == 1) {
    vec4 packedDepth = packDepth(depth);
    color = vec4(packedDepth.z, packedDepth.y, packedDepth.x, packedDepth.a);
  } else {
    color = vec4(pixelColor, 1.0);
  }
}

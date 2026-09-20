# Mesh Generation Capabilities

This document outlines the different mesh generation pipelines supported by Tetrahedron Desktop and how they function. Tetrahedron Desktop utilizes the **Gmsh** library under the hood for all core geometry processing and mesh generation.

## 1. Single-Body STL Meshing (2D and 3D)

### Supported Input
- **File Format:** `.stl`
- **Use Case:** A single closed boundary (for 3D) or a flat contour (for 2D) representing a single homogenous material or part.

### How it Works
1. **Parsing:** Tetrahedron Desktop reads the STL file and extracts the hollow boundary shell of triangles (or lines for 2D).
2. **Topology Creation:** The application uses Gmsh to map these discrete triangles into a continuous boundary surface representation.
3. **Volume Definition:** For 3D meshes, a solid internal volume is mathematically defined from the closed boundary.
4. **Meshing:** The application instructs Gmsh to fill the volume with internal 2D triangles or 3D tetrahedrons, respecting the user-defined Minimum/Maximum Mesh Size and Angle parameters from the UI.
5. **Output:** A single contiguous mesh is exported, with all elements mapped to a single default layer ID.

## 2. Multi-Layer / Multi-Body Conformal Meshing (3D only)

### Supported Input
- **File Format:** `.step` / `.stp`
- **Use Case:** Stacked layers, assemblies, or complex multi-part models where parts physically touch or overlap, and calculations require distinct layer tracking (e.g., FEM simulations with different material properties per layer).

### How it Works
When a STEP file is loaded, Tetrahedron Desktop utilizes Gmsh's robust built-in CAD processing capabilities to guarantee perfect alignment between different parts.

1. **CAD Import:** The application reads the mathematical solid definitions from the STEP file.
2. **Intersection Resolution:** Instead of treating touching bodies as separate and risking overlapping boundaries, the application instructs Gmsh to perform a geometric intersection analysis. It identifies all areas where bodies touch, splits overlapping regions, and automatically builds a single shared, unified boundary between the touching volumes.
3. **Conformal Meshing:** Gmsh's 3D mesher uses this unified boundary to generate the internal tetrahedrons. This guarantees a **100% conformal mesh**—meaning that nodes and element faces match perfectly on the interface between different layers, with no crossing elements or microscopic gaps.
4. **Layer ID Preservation:** After the mesh is generated, Tetrahedron Desktop iterates through each individual solid body and tags its internal tetrahedrons. Each tetrahedron is recorded with the ID of its parent layer. 
5. **Output:** The mesh is written to disk alongside a `3_layer_ids.txt` layer mapping. This allows downstream solvers to correctly assign distinct physical properties to each layer.

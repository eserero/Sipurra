# Task Reorganization Strategy

To align task management with the **Agent OS** specification structure, the project's tasks (managed by the `beans` CLI) have been reorganized from a flat `.beans/` directory into spec-specific subfolders.

## Reorganization Steps

1.  **New Task Location**: For every specification folder under `agent-os/specs/`, a dedicated `tasks/` subfolder was created to hold the related "beans" (task files).
    *   Example: `agent-os/specs/2026-02-23-1450-immersive-detail-screens/tasks/`

2.  **Configuration Update**: The root `.beans.yml` configuration was updated to point to the new parent directory:
    ```yaml
    beans:
        path: agent-os/specs
    ```

3.  **Recursive Discovery**: The `beans` CLI is recursive by default. By setting the path to `agent-os/specs`, it automatically scans all subdirectories (like `spec-name/tasks/`) to find and list all tasks across all features.

4.  **Cleanup**: The original `.beans/` directory was removed once all tasks were successfully moved and verified.

## Benefits
- **Contextual Alignment**: Tasks are now physically located alongside the specifications, plans, and standards they fulfill.
- **Multi-Spec Support**: The `beans list` command still provides a unified view of all project tasks, even though they are distributed across different spec folders.
- **Cleaner Root**: Removes the `.beans/` folder from the root of the project.

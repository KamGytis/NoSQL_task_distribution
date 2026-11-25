import type { ProjectRole } from "../types";
import { roleLabel } from "../projects/helpers";

type Props = { role: ProjectRole | null };

export default function RoleBadge({ role }: Props) {
    if (!role) return null;
    return <span className={`role-badge role-${role.toLowerCase()}`}>{roleLabel(role)}</span>;
}
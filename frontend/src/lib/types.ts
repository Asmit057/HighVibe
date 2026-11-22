export type ProjectRole = 'OWNER' | 'EDITOR' | 'VIEWER'
export type PreviewStatus = 'CREATING' | 'RUNNING' | 'FAILED' | 'TERMINATED'
export type ChatEventType = 'THOUGHT' | 'MESSAGE' | 'FILE_EDIT' | 'TOOL_LOG'
export type ChatEventStatus = 'PENDING' | 'CONFIRMED' | 'FAILED'

export interface UserProfile {
  id: number
  username: string
  name: string
}

export interface AuthResponse {
  token: string
  refreshToken: string
  expiresIn: number
  user: UserProfile
}

export interface ProjectSummary {
  id: number
  name: string
  description: string | null
  role: ProjectRole | null
  createdAt: string
  updatedAt: string
}

export interface Project extends ProjectSummary {
  owner: { id: number; username: string | null; name: string | null } | null
}

export interface FileNode {
  path: string
  size: number | null
  modifiedAt: string | null
}

export interface Preview {
  id: number
  projectId: number
  status: PreviewStatus
  previewUrl: string | null
  failureReason: string | null
  startedAt: string | null
  expiresAt: string | null
}

export interface Member {
  userId: number
  username: string | null
  name: string | null
  projectRole: ProjectRole
  invitedAt: string | null
  acceptedAt: string | null
}

export interface Invitation {
  projectId: number
  projectName: string
  role: ProjectRole
  invitedAt: string
}

export interface ChatEvent {
  id: number | null
  type: ChatEventType
  sequenceOrder: number
  content: string | null
  filePath: string | null
  metadata: string | null
  status: ChatEventStatus | null
}

export interface ChatMessage {
  id: number
  role: 'USER' | 'ASSISTANT' | 'SYSTEM' | 'TOOL'
  events: ChatEvent[]
  content: string | null
  tokensUsed: number | null
  createdAt: string
}

export interface Plan {
  id: number
  name: string
  priceCents: number
  price: string
  maxProjects: number | null
  maxTokensPerDay: number | null
  maxPreviews: number | null
  unlimitedAi: boolean
  purchasable: boolean
}

export interface Subscription {
  plan: Plan
  status: string
  currentPeriodEnd: string | null
  cancelAtPeriodEnd: boolean
  billingPortalAvailable: boolean
}

export interface UsageToday {
  planName: string
  tokensUsed: number
  tokenLimit: number | null
  unlimited: boolean
  generations: number
}

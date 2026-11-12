/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */

/*
 * Undo / redo of the dashboard editor (E4): snapshots of the whole source (JSON), at most 30 steps as the Control
 * Center. Changes of the same group within a short time (typing in a field, holding an arrow key) form one step.
 */

export const HISTORY_STEPS = 30
/** Changes of the same group closer than this form one step. */
export const COALESCE_MS = 800

export class SourceHistory {
  private undoStack: string[] = []
  private redoStack: string[] = []
  private lastGroup: string | null = null
  private lastAt = 0
  current: string

  constructor(
    initial: string,
    private readonly limit = HISTORY_STEPS,
  ) {
    this.current = initial
  }

  get canUndo(): boolean {
    return this.undoStack.length > 0
  }

  get canRedo(): boolean {
    return this.redoStack.length > 0
  }

  get undoCount(): number {
    return this.undoStack.length
  }

  /** Starts again with `state` (no steps). */
  reset(state: string): void {
    this.undoStack = []
    this.redoStack = []
    this.current = state
    this.lastGroup = null
    this.lastAt = 0
  }

  /**
   * Records the state after a change. `group`: changes of the same group within COALESCE_MS are one step (null: always
   * a new step). Returns whether the state was new.
   */
  record(state: string, group: string | null = null, now = Date.now()): boolean {
    if (state === this.current) return false
    const merge = group !== null && group === this.lastGroup && now - this.lastAt < COALESCE_MS && this.undoStack.length > 0
    if (!merge) {
      this.undoStack.push(this.current)
      if (this.undoStack.length > this.limit) this.undoStack.splice(0, this.undoStack.length - this.limit)
    }
    this.current = state
    this.redoStack = []
    this.lastGroup = group
    this.lastAt = now
    return true
  }

  /** The state before the last step, or null. */
  undo(): string | null {
    const previous = this.undoStack.pop()
    if (previous === undefined) return null
    this.redoStack.push(this.current)
    this.current = previous
    this.lastGroup = null
    return previous
  }

  redo(): string | null {
    const next = this.redoStack.pop()
    if (next === undefined) return null
    this.undoStack.push(this.current)
    this.current = next
    this.lastGroup = null
    return next
  }
}

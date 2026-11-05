/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */

/** Same minimum as the web service (ResourceAuth.MIN_PASSWORD_LENGTH). */
export const MIN_PASSWORD_LENGTH = 8

/** @returns the i18n key of the problem, or null if the new password can be sent */
export function passwordProblem(password: string, repeat: string): string | null {
  if (password.length < MIN_PASSWORD_LENGTH) return 'password.tooShort'
  if (password !== repeat) return 'password.mismatch'
  return null
}

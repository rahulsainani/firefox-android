/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.shopping.store

import mozilla.components.lib.state.MiddlewareV2

/**
 * Middleware typealias for review quality check.
 */
typealias ReviewQualityCheckMiddleware = MiddlewareV2<ReviewQualityCheckState, ReviewQualityCheckAction>

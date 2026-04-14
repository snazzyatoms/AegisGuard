package com.aegisguard.api;

import com.aegisguard.AegisGuard;
import com.aegisguard.api.service.ClaimBlockAccess;
import com.aegisguard.api.service.EconomyAccess;
import com.aegisguard.api.service.PlotAccess;
import com.aegisguard.api.service.ProtectionAccess;
import com.aegisguard.api.service.SelectionAccess;

/**
 * Public integration facade for AegisGuard.
 *
 * <p>Plugin developers should prefer this API over reaching into internal
 * managers directly. It exposes the currently-supported integration surface
 * while still letting the plugin evolve behind the scenes.</p>
 */
public interface AegisGuardAPI {

    AegisGuard plugin();

    String version();

    PlotAccess plots();

    ClaimBlockAccess claimBlocks();

    EconomyAccess economy();

    SelectionAccess selections();

    ProtectionAccess protection();
}

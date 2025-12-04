public void confirmClaim(Player p) {
        UUID uuid = p.getUniqueId();
        LanguageManager lang = plugin.getLanguageManager();
        
        if (!hasSelection(p)) {
            p.sendMessage(lang.getMsg(p, "must_select"));
            return;
        }

        boolean isServerClaim = selectionIsServer.getOrDefault(uuid, false);
        Location l1 = loc1.get(uuid);
        Location l2 = loc2.get(uuid);

        if (!l1.getWorld().equals(l2.getWorld())) {
            p.sendMessage(ChatColor.RED + "Corners must be in the same world!");
            return;
        }
        
        Cuboid selection = new Cuboid(l1, l2);
        int radius = Math.max(selection.getWidth(), selection.getLength()) / 2;
        
        // --- VALIDATION ---
        if (!isServerClaim) {
            if (!plugin.getWorldRules().allowClaims(p.getWorld())) {
                p.sendMessage(ChatColor.RED + "Estates are disabled in this world.");
                return;
            }

            // WorldGuard Killer Check
            if (isOverlappingServerZone(selection)) {
                if (!p.hasPermission("aegis.admin.bypass")) {
                    p.sendMessage(ChatColor.RED + "❌ " + ChatColor.BOLD + "FORBIDDEN.");
                    p.sendMessage(ChatColor.RED + "This land is protected by Aegis Divine.");
                    p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 1f, 0.5f);
                    return;
                } else {
                    p.sendMessage(ChatColor.YELLOW + "⚠ Admin Bypass: Claiming inside Server Zone.");
                }
            }

            if (plugin.getEstateManager().isOverlapping(selection)) {
                p.sendMessage(lang.getMsg(p, "claim_failed_overlap"));
                return;
            }

            int maxRadius = plugin.getConfig().getInt("estates.max_radius", 100);
            if (radius > maxRadius && !p.hasPermission("aegis.admin.bypass")) {
                p.sendMessage(lang.getMsg(p, "claim_failed_limit").replace("%max%", String.valueOf(maxRadius)));
                return;
            }
            
            double cost = plugin.getEconomy().calculateClaimCost(selection);
            if (plugin.getConfig().getBoolean("economy.enabled", true) && cost > 0) {
                if (!plugin.getEconomy().withdraw(p, cost)) {
                    p.sendMessage(lang.getMsg(p, "claim_failed_money").replace("%cost%", String.valueOf(cost)));
                    return;
                }
                p.sendMessage(ChatColor.GREEN + "Deducted $" + cost + " from your wallet.");
            }
        }

        // --- CREATION ---
        String name;
        if (isServerClaim) {
            name = "Server Zone";
        } else {
            String format = plugin.getConfig().getString("estates.naming.private_format", "%player%'s Estate");
            name = format.replace("%player%", p.getName());
        }
        
        Estate estate = plugin.getEstateManager().createEstate(p, selection, name, false); 
        
        if (isServerClaim && estate != null) {
            plugin.getEstateManager().transferOwnership(estate, Estate.SERVER_UUID, false);
            estate.setFlag("build", false);
            estate.setFlag("pvp", false);
            estate.setFlag("safe_zone", true);
        }

        if (estate != null) {
            p.sendMessage(lang.getMsg(p, "claim_success")
                .replace("%type%", isServerClaim ? "Server Zone" : lang.getTerm("type_private"))
                .replace("%name%", name));
            
            // --- NEW: mcMMO XP REWARD ---
            if (!isServerClaim && plugin.getMcMMO() != null) {
                plugin.getMcMMO().giveClaimExp(p);
            }
            // ----------------------------
            
            if (!isServerClaim && plugin.getConfig().getBoolean("estates.consume_wand_on_claim", true)) {
                consumeWand(p);
            }
            
            loc1.remove(uuid);
            loc2.remove(uuid);
            selectionIsServer.remove(uuid);
        }
    }

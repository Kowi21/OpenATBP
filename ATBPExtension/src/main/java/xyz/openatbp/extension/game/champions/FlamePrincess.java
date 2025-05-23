package xyz.openatbp.extension.game.champions;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.*;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

public class FlamePrincess extends UserActor {
    private static final int Q_GLOBAL_COOLDOWN = 250;
    private static final int PASSIVE_COOLDOWN = 10000;
    private static final int W_POLY_DURATION = 3000;
    private static final double Q_BURST_DMG_MULTIPLIER = 1.2;
    private final int W_CAST_DELAY = 1000;
    private static final int E_DASH_COOLDOWN = 350;
    private static final int E_DURATION = 5000;
    private static final int E_DASH_SPEED = 15;

    private boolean passiveEnabled = false;
    private long lastPassiveUsage = 0;
    private int ultUses = 0;
    private int dashTime = 0;
    private boolean wUsed = false;
    private long ultStartTime = 0;
    private long lastPolymorphTime = 0;
    private float fpScale = 1;

    private enum Form {
        NORMAL,
        ULT
    }

    private Form form = Form.NORMAL;

    public FlamePrincess(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if (this.form == Form.ULT && System.currentTimeMillis() - this.ultStartTime >= E_DURATION) {
            canCast[2] = false; // to be on the safe side :D
            endUlt();
        }
        if (this.form == Form.ULT) {
            RoomHandler handler = parentExt.getRoomHandler(this.room.getName());
            for (Actor a : Champion.getActorsInRadius(handler, this.location, 2)) {
                if (a.getTeam() != this.team && isNeitherTowerNorAlly(a)) {
                    JsonNode attackData = this.parentExt.getAttackData(getAvatar(), "spell3");
                    double damage = (double) this.getSpellDamage(attackData, false) / 10;
                    a.addToDamageQueue(this, damage, attackData, true);
                    handlePassive();
                }
            }
        }
        if (System.currentTimeMillis() - lastPolymorphTime <= W_POLY_DURATION) {
            for (Actor a : this.parentExt.getRoomHandler(this.room.getName()).getPlayers()) {
                boolean polymorphActive = a.getState(ActorState.POLYMORPH);
                if (polymorphActive) {
                    RoomHandler handler = parentExt.getRoomHandler(room.getName());
                    List<Actor> actorsInRadius =
                            Champion.getActorsInRadius(handler, a.getLocation(), 2f);
                    actorsInRadius.remove(a);

                    for (Actor affectedActor : actorsInRadius) {
                        if (isNeitherStructureNorAlly(affectedActor)) {
                            handlePassive();
                            JsonNode spellData = this.parentExt.getAttackData("flame", "spell2");

                            double damage = getSpellDamage(spellData, false) / 10d;
                            affectedActor.addToDamageQueue(this, damage, spellData, true);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void handleSwapToPoly(int duration) {
        super.handleSwapToPoly(duration);
        if (this.passiveEnabled) {
            ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_flame_passive");
        }
        if (this.fpScale == 1.5f) {
            ExtensionCommands.scaleActor(parentExt, room, id, 0.6667f);
            this.fpScale = 1;
        }
    }

    @Override
    public void handleSwapFromPoly() {
        String bundle = this.form == Form.ULT ? "flame_ult" : getSkinAssetBundle();
        ExtensionCommands.swapActorAsset(this.parentExt, this.room, this.id, bundle);
        if (form == Form.ULT) {
            this.fpScale = 1.5f;
            ExtensionCommands.scaleActor(parentExt, room, id, 1.5f);
        }
        if (this.passiveEnabled) {
            ExtensionCommands.createActorFX(
                    this.parentExt,
                    this.room,
                    this.id,
                    "flame_princess_passive_flames",
                    1000 * 60 * 15,
                    this.id + "_flame_passive",
                    true,
                    "",
                    false,
                    false,
                    this.team);
        }
    }

    @Override
    public void die(Actor a) {
        super.die(a);
        if (this.form == Form.ULT) {
            endUlt();
            ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "flameE");
        }
        if (passiveEnabled) {
            passiveEnabled = false;
            ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_flame_passive");
            ExtensionCommands.actorAbilityResponse(
                    parentExt, player, "passive", true, PASSIVE_COOLDOWN, 0);
        }
    }

    @Override
    public void attack(Actor a) {
        this.applyStopMovingDuringAttack();
        PassiveAttack passiveAttack = new PassiveAttack(a, this.handleAttack(a));
        scheduleTask(
                new RangedAttack(a, passiveAttack, "flame_princess_projectile"),
                BASIC_ATTACK_DELAY);
    }

    @Override
    public boolean canAttack() {
        boolean notAllowed = System.currentTimeMillis() - this.ultStartTime < 500;
        if (notAllowed) return false;
        return super.canAttack();
    }

    @Override
    public boolean canMove() {
        if (this.wUsed) return false;
        else return super.canMove();
    }

    public void handlePassive() {
        if (canTriggerPassive()) {
            ExtensionCommands.createActorFX(
                    this.parentExt,
                    this.room,
                    this.id,
                    "flame_princess_passive_flames",
                    1000 * 60 * 15,
                    this.id + "_flame_passive",
                    true,
                    "",
                    false,
                    false,
                    this.team);
            ExtensionCommands.playSound(
                    this.parentExt,
                    this.room,
                    this.id,
                    "sfx_flame_princess_passive_ignite",
                    this.location);
            passiveEnabled = true;
        }
    }

    @Override
    public void useAbility(
            int ability,
            JsonNode spellData,
            int cooldown,
            int gCooldown,
            int castDelay,
            Point2D dest) {
        switch (ability) {
            case 1:
                this.canCast[0] = false;
                basicAttackReset();
                try {
                    stopMoving();
                    if (getHealth() > 0) {
                        Line2D abilityLine = Champion.getAbilityLine(location, dest, 8f);
                        ExtensionCommands.playSound(
                                parentExt,
                                room,
                                this.id,
                                "sfx_flame_princess_cone_of_flame",
                                this.location);
                        fireProjectile(
                                new FlameProjectile(
                                        this.parentExt,
                                        this,
                                        abilityLine,
                                        8f,
                                        0.5f,
                                        "projectile_flame_cone"),
                                location,
                                dest,
                                8f);
                    }
                } catch (Exception exception) {
                    logExceptionMessage(avatar, ability);
                    exception.printStackTrace();
                }
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt,
                        this.player,
                        "q",
                        true,
                        getReducedCooldown(cooldown),
                        gCooldown);
                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, gCooldown, dest), gCooldown);
                break;
            case 2:
                this.canCast[1] = false;
                try {
                    stopMoving();
                    this.wUsed = true;
                    ExtensionCommands.createWorldFX(
                            this.parentExt,
                            this.player.getLastJoinedRoom(),
                            this.id,
                            "fx_target_ring_2",
                            "flame_w",
                            1500,
                            (float) dest.getX(),
                            (float) dest.getY(),
                            true,
                            this.team,
                            0f);
                    Runnable fxDelay =
                            () ->
                                    ExtensionCommands.createWorldFX(
                                            this.parentExt,
                                            this.player.getLastJoinedRoom(),
                                            this.id,
                                            "flame_princess_polymorph_fireball",
                                            this.id + "_flame_w_polymorph",
                                            2000,
                                            (float) dest.getX(),
                                            (float) dest.getY(),
                                            false,
                                            this.team,
                                            0f);
                    int delay = 500;
                    scheduleTask(fxDelay, delay);
                } catch (Exception exception) {
                    logExceptionMessage(avatar, ability);
                    exception.printStackTrace();
                }
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt,
                        this.player,
                        "w",
                        true,
                        getReducedCooldown(cooldown),
                        gCooldown);
                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, gCooldown, dest), castDelay);
                break;
            case 3:
                this.canCast[2] = false;
                if (this.ultUses == 0) {
                    this.ultUses++;
                    this.form = Form.ULT;
                    this.ultStartTime = System.currentTimeMillis();
                    this.stopMoving(castDelay);
                    ExtensionCommands.playSound(
                            this.parentExt,
                            this.room,
                            this.id,
                            "vo/vo_flame_princess_flame_form",
                            this.getLocation());
                    ExtensionCommands.playSound(
                            this.parentExt,
                            this.room,
                            this.id,
                            "sfx_flame_princess_flame_form",
                            this.getLocation());
                    ExtensionCommands.swapActorAsset(
                            this.parentExt, this.room, this.id, "flame_ult");
                    ExtensionCommands.createActorFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            "flame_princess_ultimate_aoe",
                            5500,
                            this.id + "flameE",
                            true,
                            "",
                            true,
                            false,
                            this.team);
                    ExtensionCommands.addStatusIcon(
                            parentExt,
                            player,
                            "ultDurationIcon",
                            "flame_spell_3_description",
                            "icon_flame_s3",
                            E_DURATION);
                    ExtensionCommands.scaleActor(this.parentExt, this.room, this.id, 1.5f);
                    this.fpScale = 1.5f;
                    ExtensionCommands.actorAbilityResponse(
                            this.parentExt, this.player, "e", true, castDelay, 0);
                    scheduleTask(
                            abilityRunnable(ability, spellData, cooldown, gCooldown, dest),
                            castDelay);
                } else if (this.ultUses < 5) {
                    if (canDash()) {
                        this.ultUses++;
                        Point2D ogLocation = this.location;
                        Point2D dashLocation = this.dash(dest, false, E_DASH_SPEED);
                        double time = ogLocation.distance(dashLocation) / E_DASH_SPEED;
                        this.dashTime = (int) (time * 1000);
                        ExtensionCommands.actorAnimate(
                                this.parentExt, this.room, this.id, "run", this.dashTime, false);
                        int remainingTime = (int) (System.currentTimeMillis() - ultStartTime);

                        if (remainingTime + dashTime > E_DURATION) { // handle last moment dashing
                            int timeNeeded = remainingTime + dashTime - E_DURATION;
                            this.ultStartTime += timeNeeded; // add time to complete anim
                            this.ultUses = 5; // disable further dashing
                        }
                        scheduleTask(
                                abilityRunnable(ability, spellData, cooldown, gCooldown, dest),
                                E_DASH_COOLDOWN);
                    } else {
                        ExtensionCommands.playSound(
                                this.parentExt,
                                this.player,
                                this.id,
                                "not_allowed_error",
                                new Point2D.Float(0, 0));
                    }
                }
                break;
        }
    }

    private int getBaseUltCooldown() {
        return ChampionData.getBaseAbilityCooldown(this, 3);
    }

    private boolean canTriggerPassive() {
        long currentTime = System.currentTimeMillis();
        return !passiveEnabled && currentTime - lastPassiveUsage >= PASSIVE_COOLDOWN;
    }

    private void endUlt() {
        this.form = Form.NORMAL;
        if (this.fpScale == 1.5f) {
            ExtensionCommands.scaleActor(parentExt, room, id, 0.6667f);
            this.fpScale = 1;
        }
        if (!this.getState(ActorState.POLYMORPH)) { // poly asset swap handled elsewhere
            ExtensionCommands.swapActorAsset(parentExt, room, id, getSkinAssetBundle());
        }
        ExtensionCommands.removeFx(parentExt, room, id + "flameE");
        ExtensionCommands.removeStatusIcon(parentExt, player, "ultDurationIcon");
        int delay = getReducedCooldown(getBaseUltCooldown());
        Runnable handleECooldown =
                () -> {
                    this.ultUses = 0;
                    this.canCast[2] = true;
                };
        ExtensionCommands.actorAbilityResponse(parentExt, player, "e", true, delay, 0);
        scheduleTask(handleECooldown, delay);
    }

    private FlameAbilityRunnable abilityRunnable(
            int ability, JsonNode spelldata, int cooldown, int gCooldown, Point2D dest) {
        return new FlameAbilityRunnable(ability, spelldata, cooldown, gCooldown, dest);
    }

    private class FlameAbilityRunnable extends AbilityRunnable {

        public FlameAbilityRunnable(
                int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            int delay = getReducedCooldown(cooldown) - Q_GLOBAL_COOLDOWN;
            Runnable enableQCasting = () -> canCast[0] = true;
            scheduleTask(enableQCasting, delay);
        }

        @Override
        protected void spellW() {
            ExtensionCommands.playSound(
                    parentExt, room, "", "sfx_flame_princess_projectile_explode", this.dest);
            wUsed = false;
            RoomHandler roomHandler =
                    parentExt.getRoomHandler(player.getLastJoinedRoom().getName());
            List<Actor> affectedUsers =
                    Champion.getActorsInRadius(roomHandler, this.dest, 2).stream()
                            .filter(a -> a.getTeam() != FlamePrincess.this.team)
                            .collect(Collectors.toList());

            for (Actor a : affectedUsers) {
                if (a.getActorType() == ActorType.PLAYER) { // poly for bot handled elsewhere
                    UserActor userActor = (UserActor) a;
                    userActor.addState(ActorState.POLYMORPH, 0d, 3000);
                    lastPolymorphTime = System.currentTimeMillis();
                }
                double newDamage = getSpellDamage(spellData, true);
                if (isNeitherTowerNorAlly(a)) {
                    JsonNode attackData = parentExt.getAttackData(getAvatar(), "spell2");
                    a.addToDamageQueue(FlamePrincess.this, newDamage, attackData, false);
                    handlePassive();
                }
            }

            int delay = getReducedCooldown(cooldown) - W_CAST_DELAY;
            Runnable enableWCasting = () -> canCast[1] = true;
            scheduleTask(enableWCasting, delay);
        }

        @Override
        protected void spellE() {
            if (ultUses < 4) canCast[2] = true;
            if (ultUses == 4) {
                endUlt();
            }
        }

        @Override
        protected void spellPassive() {}
    }

    private class FlameProjectile extends Projectile {

        private boolean hitPlayer = false;

        public FlameProjectile(
                ATBPExtension parentExt,
                UserActor owner,
                Line2D path,
                float speed,
                float hitboxRadius,
                String id) {
            super(parentExt, owner, path, speed, hitboxRadius, id);
        }

        @Override
        public void hit(Actor victim) {
            if (this.hitPlayer) return;
            this.hitPlayer = true;
            handlePassive();
            JsonNode attackData = parentExt.getAttackData(getAvatar(), "spell1");
            double damage = getSpellDamage(attackData, true);
            victim.addToDamageQueue(FlamePrincess.this, damage, attackData, false);

            ExtensionCommands.playSound(
                    parentExt, room, "", "akubat_projectileHit1", victim.getLocation());
            ExtensionCommands.createActorFX(
                    parentExt,
                    room,
                    this.id,
                    "flame_princess_projectile_large_explosion",
                    1000,
                    "flame_explosion",
                    false,
                    "",
                    false,
                    false,
                    team);
            ExtensionCommands.createActorFX(
                    parentExt,
                    room,
                    this.id,
                    "flame_princess_cone_of_flames",
                    1500,
                    "flame_cone",
                    false,
                    "",
                    true,
                    false,
                    team);

            Point2D hitPoint = victim.getLocation();

            RoomHandler handler = parentExt.getRoomHandler(room.getName());
            for (Actor a : Champion.getActorsInRadius(handler, hitPoint, 1f)) {
                if (isNeitherTowerNorAlly(a) && !a.equals(victim)) {
                    double dmg = damage *= Q_BURST_DMG_MULTIPLIER;
                    a.addToDamageQueue(FlamePrincess.this, dmg, attackData, false);
                }
            }
            destroy();
        }
    }

    private class PassiveAttack implements Runnable {

        Actor target;
        boolean crit;

        PassiveAttack(Actor target, boolean crit) {
            this.target = target;
            this.crit = crit;
        }

        @Override
        public void run() {
            double damage = getPlayerStat("attackDamage");
            if (crit) {
                damage *= 1.25;
                damage = handleGrassSwordProc(damage);
            }
            new Champion.DelayedAttack(
                            parentExt, FlamePrincess.this, target, (int) damage, "basicAttack")
                    .run();
            if (FlamePrincess.this.passiveEnabled && (target.getActorType() != ActorType.TOWER)) {
                FlamePrincess.this.passiveEnabled = false;
                ExtensionCommands.removeFx(parentExt, room, id + "_flame_passive");
                ExtensionCommands.actorAbilityResponse(
                        parentExt, player, "passive", true, PASSIVE_COOLDOWN, 0);
                lastPassiveUsage = System.currentTimeMillis();
                ExtensionCommands.createActorFX(
                        parentExt,
                        room,
                        target.getId(),
                        "flame_princess_dot",
                        3000,
                        "flame_passive_burn",
                        true,
                        "",
                        false,
                        false,
                        team);

                int passiveDamage = getSpellDamage(getSpellData(4), true) / 2;
                JsonNode attackData = parentExt.getAttackData(avatar, "spell4");

                Runnable damageActor =
                        () -> {
                            target.addToDamageQueue(
                                    FlamePrincess.this, passiveDamage, attackData, true);
                            ExtensionCommands.createActorFX(
                                    parentExt,
                                    room,
                                    target.getId(),
                                    "_playerGotHitSparks",
                                    500,
                                    target.getId() + "_hit" + Math.random(),
                                    true,
                                    "",
                                    true,
                                    false,
                                    target.getTeam());
                        };

                for (int i = 0; i < 6; i++) {
                    int delay = (i * 500) + 500;
                    scheduleTask(damageActor, delay);
                }
            }
            if (FlamePrincess.this.passiveEnabled
                    && (target.getActorType() == ActorType.BASE
                            || target.getActorType() == ActorType.TOWER)) {
                FlamePrincess.this.passiveEnabled = false;
                ExtensionCommands.removeFx(parentExt, room, id + "_flame_passive");
                ExtensionCommands.actorAbilityResponse(
                        parentExt, player, "passive", true, PASSIVE_COOLDOWN, 0);
                lastPassiveUsage = System.currentTimeMillis();
            }
        }
    }
}

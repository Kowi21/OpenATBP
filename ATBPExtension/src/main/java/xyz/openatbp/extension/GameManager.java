package xyz.openatbp.extension;

import java.awt.geom.Point2D;
import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.entities.variables.RoomVariable;
import com.smartfoxserver.v2.entities.variables.SFSRoomVariable;
import com.smartfoxserver.v2.entities.variables.SFSUserVariable;
import com.smartfoxserver.v2.entities.variables.UserVariable;
import com.smartfoxserver.v2.exceptions.SFSVariableException;

import xyz.openatbp.extension.game.actors.UserActor;

public class GameManager {

    // bh1 = Blue Health 1 ph1 = Purple Health 1. Numbers refer to top,bottom,and outside
    // respectively.
    public static final String[] SPAWNS = {
        "bh1",
        "bh2",
        "bh3",
        "ph1",
        "ph2",
        "ph3",
        "keeoth",
        "goomonster",
        "hugwolf",
        "gnomes",
        "ironowls",
        "grassbear"
    };
    private static ObjectMapper objectMapper = new ObjectMapper();

    public static void addPlayer(
            Room room, ATBPExtension parentExt) { // Sends player info to client
        for (User user : room.getUserList()) {
            ISFSObject playerInfo = user.getVariable("player").getSFSObjectValue();
            int id = user.getId();
            String name = playerInfo.getUtfString("name");
            String champion = playerInfo.getUtfString("avatar");
            int team = playerInfo.getInt("team");
            String backpack = playerInfo.getUtfString("backpack");
            String tid = playerInfo.getUtfString("tegid");
            int elo = parentExt.getElo(tid);
            boolean tournamentEligible = playerInfo.getBool("isTournamentEligible");
            ExtensionCommands.addUser(
                    parentExt,
                    room,
                    id,
                    name,
                    champion,
                    team,
                    tid,
                    backpack,
                    elo,
                    tournamentEligible);
        }
    }

    public static void loadPlayers(
            Room room, ATBPExtension parentExt) { // Loads the map for everyone
        String groupID = room.getGroupId();
        for (User u : room.getUserList()) {
            ISFSObject data = new SFSObject();
            if (groupID.equals("Practice")
                    || (room.getName().contains("custom") && room.getMaxUsers() == 2)) {
                data.putUtfString("set", "AT_1L_Arena");
            } else {
                data.putUtfString("set", "AT_2L_Arena");
            }
            int maxUsers = room.getMaxUsers();
            int userSize = room.getUserList().size();
            data.putUtfString("soundtrack", "music_main1");
            data.putInt("roomId", room.getId());
            data.putUtfString("roomName", room.getName());
            data.putInt("capacity", maxUsers);
            data.putInt("botCount", maxUsers - userSize);
            parentExt.send("cmd_load_room", data, u);
        }
    }

    public static boolean playersLoaded(ArrayList<User> users, int gameSize) {
        int num = 0;
        for (User u : users) {
            if (u.getProperty("joined") != null && (boolean) u.getProperty("joined")) num++;
        }
        return num == gameSize;
    }

    public static boolean playersReady(Room room) { // Checks if all clients are ready
        int ready = 0;
        ArrayList<User> users = (ArrayList<User>) room.getUserList();
        for (int i = 0; i < users.size(); i++) {
            // Console.debugLog(users.get(i).getSession());
            if (users.get(i).getSession().getProperty("ready") == null) return false;
            if ((boolean) users.get(i).getSession().getProperty("ready")) ready++;
        }
        return ready == users.size();
    }

    public static void sendAllUsers(
            ATBPExtension parentExt, ISFSObject data, String cmd, Room room) {
        ArrayList<User> users = (ArrayList<User>) room.getUserList();
        for (int i = 0; i < users.size(); i++) {
            parentExt.send(cmd, data, users.get(i));
        }
    }

    public static void initializeGame(Room room, ATBPExtension parentExt)
            throws SFSVariableException {
        room.setProperty("state", 2);
        int blueNum = 0;
        int purpleNum = 0;
        initializeMap(room, parentExt);
        for (User u : room.getUserList()) {
            ISFSObject playerInfo = u.getVariable("player").getSFSObjectValue();
            int team = playerInfo.getInt("team");
            float px = 0f;
            float pz = 0f;
            if (team == 0) {
                if (room.getGroupId().equals("Practice")) {
                    px = (float) MapData.L1_PURPLE_SPAWNS[purpleNum].getX();
                    pz = (float) MapData.L1_PURPLE_SPAWNS[purpleNum].getY();
                } else {
                    px = (float) MapData.L2_PURPLE_SPAWNS[purpleNum].getX();
                    pz = (float) MapData.L2_PURPLE_SPAWNS[purpleNum].getY();
                }
                purpleNum++;
            }
            if (team == 1) {
                if (room.getGroupId().equals("Practice")) {
                    px = (float) MapData.L1_PURPLE_SPAWNS[blueNum].getX() * -1;
                    pz = (float) MapData.L1_PURPLE_SPAWNS[blueNum].getY();
                } else {
                    px = (float) MapData.L2_PURPLE_SPAWNS[blueNum].getX() * -1;
                    pz = (float) MapData.L2_PURPLE_SPAWNS[blueNum].getY();
                }
                blueNum++;
            }
            String id = String.valueOf(u.getId());
            String actor = playerInfo.getUtfString("avatar");
            Point2D location = new Point2D.Float(px, pz);
            ExtensionCommands.createActor(parentExt, room, id, actor, location, 0f, team);

            ISFSObject updateData = new SFSObject();
            updateData.putUtfString("id", String.valueOf(u.getId()));
            int champMaxHealth =
                    parentExt
                            .getActorStats(playerInfo.getUtfString("avatar"))
                            .get("health")
                            .asInt();
            updateData.putInt("currentHealth", champMaxHealth);
            updateData.putInt("maxHealth", champMaxHealth);
            updateData.putDouble("pHealth", 1);
            updateData.putInt("xp", 0);
            updateData.putDouble("pLevel", 0);
            updateData.putInt("level", 1);
            updateData.putInt("availableSpellPoints", 1);
            updateData.putLong("timeSinceBasicAttack", 0);
            // SP_CATEGORY 1-5 TBD
            updateData.putInt("sp_category1", 0);
            updateData.putInt("sp_category2", 0);
            updateData.putInt("sp_category3", 0);
            updateData.putInt("sp_category4", 0);
            updateData.putInt("sp_category5", 0);
            updateData.putInt("deaths", 0);
            updateData.putInt("assists", 0);
            JsonNode actorStats = parentExt.getActorStats(playerInfo.getUtfString("avatar"));
            for (Iterator<String> it = actorStats.fieldNames(); it.hasNext(); ) {
                String k = it.next();
                updateData.putDouble(k, actorStats.get(k).asDouble());
            }
            UserVariable userStat = new SFSUserVariable("stats", updateData);
            u.setVariable(userStat);
            ExtensionCommands.updateActorData(parentExt, room, updateData);
        }
        Point2D guardianLoc = MapData.getGuardianLocationData(0, room.getGroupId());
        Point2D guardianLoc2 = MapData.getGuardianLocationData(1, room.getGroupId());
        ExtensionCommands.moveActor(
                parentExt,
                room,
                "gumball0",
                guardianLoc,
                new Point2D.Float((float) (guardianLoc.getX() + 1f), (float) guardianLoc.getY()),
                0.01f,
                true);
        ExtensionCommands.moveActor(
                parentExt,
                room,
                "gumball1",
                guardianLoc2,
                new Point2D.Float((float) (guardianLoc2.getX() - 1f), (float) guardianLoc2.getY()),
                0.01f,
                true);
        try { // Sets all the room variables once the game is about to begin
            setRoomVariables(room);
        } catch (SFSVariableException e) { // TODO: Disconnect all players if this fails
            e.printStackTrace();
        }

        for (User u : room.getUserList()) {
            ISFSObject data = new SFSObject();
            parentExt.send("cmd_match_starting", data, u); // Starts the game for everyone
        }
        ExtensionCommands.playSound(
                parentExt, room, "global", "announcer/welcome", new Point2D.Float(0, 0));
        ExtensionCommands.playSound(
                parentExt,
                room,
                "music",
                "",
                new Point2D.Float(0, 0)); // turn off char select music
        parentExt.startScripts(room); // Starts the background scripts for the game
    }

    private static void setRoomVariables(Room room) throws SFSVariableException {
        ISFSObject spawnTimers = new SFSObject();
        for (String s : SPAWNS) { // Adds in spawn timers for all mobs/health. AKA time dead
            spawnTimers.putInt(s, 0);
        }
        ISFSObject teamScore = new SFSObject();
        teamScore.putInt("blue", 0);
        teamScore.putInt("purple", 0);
        ISFSObject mapData = new SFSObject();
        mapData.putBool("blueUnlocked", false);
        mapData.putBool("purpleUnlocked", false);
        RoomVariable scoreVar = new SFSRoomVariable("score", teamScore);
        List<RoomVariable> variables = new ArrayList<>();
        RoomVariable spawnVar = new SFSRoomVariable("spawns", spawnTimers);
        RoomVariable mapVar = new SFSRoomVariable("map", mapData);
        variables.add(scoreVar);
        variables.add(spawnVar);
        variables.add(mapVar);
        room.setVariables(variables);
    }

    private static void initializeMap(Room room, ATBPExtension parentExt) {
        String roomStr = room.getGroupId();
        ExtensionCommands.createActor(parentExt, room, MapData.getBaseActorData(0, roomStr));
        ExtensionCommands.createActor(parentExt, room, MapData.getBaseActorData(1, roomStr));

        spawnTowers(room, parentExt);
        spawnAltars(room, parentExt);
        spawnHealth(room, parentExt);

        ExtensionCommands.createActor(parentExt, room, MapData.getGuardianActorData(0, roomStr));
        ExtensionCommands.createActor(parentExt, room, MapData.getGuardianActorData(1, roomStr));
    }

    private static void spawnTowers(Room room, ATBPExtension parentExt) {
        String roomStr = room.getGroupId();
        if (!roomStr.equalsIgnoreCase("practice")) {
            ExtensionCommands.createActor(
                    parentExt, room, MapData.getTowerActorData(0, 1, roomStr));
            ExtensionCommands.createActor(
                    parentExt, room, MapData.getTowerActorData(0, 2, roomStr));
            ExtensionCommands.createActor(
                    parentExt, room, MapData.getTowerActorData(1, 1, roomStr));
            ExtensionCommands.createActor(
                    parentExt, room, MapData.getTowerActorData(1, 2, roomStr));

            ExtensionCommands.createActor(
                    parentExt, room, MapData.getBaseTowerActorData(0, roomStr));
            ExtensionCommands.createActor(
                    parentExt, room, MapData.getBaseTowerActorData(1, roomStr));
        } else {
            ExtensionCommands.createActor(
                    parentExt, room, MapData.getTowerActorData(0, 1, roomStr));
            ExtensionCommands.createActor(
                    parentExt, room, MapData.getTowerActorData(1, 4, roomStr));

            ExtensionCommands.createActor(
                    parentExt, room, MapData.getBaseTowerActorData(0, roomStr));
            ExtensionCommands.createActor(
                    parentExt, room, MapData.getBaseTowerActorData(1, roomStr));
        }
    }

    private static void spawnAltars(Room room, ATBPExtension parentExt) {
        if (room.getGroupId().equalsIgnoreCase("practice")) {
            ExtensionCommands.createActor(
                    parentExt, room, MapData.getAltarActorData(0, room.getGroupId()));
            ExtensionCommands.createActor(
                    parentExt, room, MapData.getAltarActorData(1, room.getGroupId()));

        } else {
            ExtensionCommands.createActor(
                    parentExt, room, MapData.getAltarActorData(0, room.getGroupId()));
            ExtensionCommands.createActor(
                    parentExt, room, MapData.getAltarActorData(1, room.getGroupId()));
            ExtensionCommands.createActor(
                    parentExt, room, MapData.getAltarActorData(2, room.getGroupId()));
        }
    }

    private static void spawnHealth(Room room, ATBPExtension parentExt) {
        if (room.getGroupId().equalsIgnoreCase("practice")) {
            ExtensionCommands.createActor(
                    parentExt, room, MapData.getHealthActorData(0, room.getGroupId(), -1));
            ExtensionCommands.createActor(
                    parentExt, room, MapData.getHealthActorData(1, room.getGroupId(), -1));
        } else {
            ExtensionCommands.createActor(
                    parentExt, room, MapData.getHealthActorData(0, room.getGroupId(), 0));
            ExtensionCommands.createActor(
                    parentExt, room, MapData.getHealthActorData(0, room.getGroupId(), 1));
            ExtensionCommands.createActor(
                    parentExt, room, MapData.getHealthActorData(0, room.getGroupId(), 2));
            ExtensionCommands.createActor(
                    parentExt, room, MapData.getHealthActorData(1, room.getGroupId(), 0));
            ExtensionCommands.createActor(
                    parentExt, room, MapData.getHealthActorData(1, room.getGroupId(), 1));
            ExtensionCommands.createActor(
                    parentExt, room, MapData.getHealthActorData(1, room.getGroupId(), 2));
        }
    }

    public static JsonNode getTeamData(
            ATBPExtension parentExt,
            HashMap<User, UserActor> dcPlayers,
            int team,
            Room room,
            boolean isRankedMatch) {
        final String[] STATS = {
            "damageDealtChamps",
            "damageReceivedPhysical",
            "damageReceivedSpell",
            "spree",
            "damageReceivedTotal",
            "damageDealtSpell",
            "score",
            "timeDead",
            "damageDealtTotal",
            "damageDealtPhysical"
        };
        /*
        Stats:
            damageDealtChamps
            damageReceivedPhysical
            damageReceivedSpell
            name?
            spree
            deaths
            damageReceivedTotal
            assists
            jungleMobs?
            kills
            minions?
            damageDealtSpell
            score
            timeDead
            healthPickUps?
            playerName?
            damageDealtTotal
            damageDealtPhysical

         */
        int coins = isRankedMatch ? 100 : 0;
        ObjectNode node = objectMapper.createObjectNode();
        for (User u : room.getUserList()) {
            UserActor ua =
                    parentExt.getRoomHandler(room.getName()).getPlayer(String.valueOf(u.getId()));
            if (ua.getTeam() == team) {
                ObjectNode player = objectMapper.createObjectNode();
                ISFSObject playerVar = u.getVariable("player").getSFSObjectValue();
                player.put("id", u.getId());
                for (String s : STATS) {
                    if (ua.hasGameStat(s)) player.put(s, ua.getGameStat(s));
                }
                player.put("name", playerVar.getUtfString("name"));
                player.put("kills", ua.getStat("kills"));
                player.put("deaths", ua.getStat("deaths"));
                player.put("assists", ua.getStat("assists"));
                player.put("playerName", ua.getFrame());
                player.put("myElo", (double) playerVar.getInt("elo"));
                player.put("coins", coins);
                player.put(
                        "prestigePoints", 10); // Just going to have this be a flat amount for now
                node.set(String.valueOf(u.getId()), player);
            }
        }
        if (!dcPlayers.isEmpty()) {
            for (Map.Entry<User, UserActor> entry : dcPlayers.entrySet()) {
                UserActor ua = entry.getValue();
                if (ua.getTeam() == team) {
                    ObjectNode player = objectMapper.createObjectNode();
                    User u = entry.getKey();
                    ISFSObject playerVar = u.getVariable("player").getSFSObjectValue();
                    for (String s : STATS) {
                        if (ua.hasGameStat(s)) player.put(s, ua.getGameStat(s));
                    }
                    player.put("name", playerVar.getUtfString("name"));
                    player.put("kills", ua.getStat("kills"));
                    player.put("deaths", ua.getStat("deaths"));
                    player.put("assists", ua.getStat("assists"));
                    player.put("playerName", ua.getFrame());
                    player.put("myElo", (double) playerVar.getInt("elo"));
                    player.put("coins", coins);
                    player.put(
                            "prestigePoints",
                            10); // Just going to have this be a flat amount for now
                    node.set(String.valueOf(u.getId()), player);
                }
            }
        }
        return node;
    }

    public static JsonNode getGlobalTeamData(
            ATBPExtension parentExt, HashMap<User, UserActor> dcPlayers, Room room) {
        double killsA = 0;
        double killsB = 0;
        double deathsA = 0;
        double deathsB = 0;
        double assistsA = 0;
        double assistsB = 0;
        double scoreA = 0;
        double scoreB = 0;

        for (UserActor ua : parentExt.getRoomHandler(room.getName()).getPlayers()) {
            if (ua.getTeam() == 0) {
                killsA += ua.getStat("kills");
                deathsA += ua.getStat("deaths");
                assistsA += ua.getStat("assists");
                if (ua.hasGameStat("score")) scoreA += ua.getGameStat("score");
            } else {
                killsB += ua.getStat("kills");
                deathsB += ua.getStat("deaths");
                assistsB += ua.getStat("assists");
                if (ua.hasGameStat("score")) scoreB += ua.getGameStat("score");
            }
        }
        if (!dcPlayers.isEmpty()) {
            for (Map.Entry<User, UserActor> entry : dcPlayers.entrySet()) {
                UserActor ua = entry.getValue();
                if (ua.getTeam() == 0) {
                    killsA += ua.getStat("kills");
                    deathsA += ua.getStat("deaths");
                    assistsA += ua.getStat("assists");
                    if (ua.hasGameStat("score")) scoreA += ua.getGameStat("score");
                } else {
                    killsB += ua.getStat("kills");
                    deathsB += ua.getStat("deaths");
                    assistsB += ua.getStat("assists");
                    if (ua.hasGameStat("score")) scoreB += ua.getGameStat("score");
                }
            }
        }
        ObjectNode node = objectMapper.createObjectNode();
        node.put("killsA", killsA);
        node.put("killsB", killsB);
        node.put("deathsA", deathsA);
        node.put("deathsB", deathsB);
        node.put("scoreA", scoreA);
        node.put("scoreB", scoreB);
        node.put("assistsA", assistsA);
        node.put("assistsB", assistsB);
        return node;
    }
}

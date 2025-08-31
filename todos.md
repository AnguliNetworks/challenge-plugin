# Todos
- [x] Why are we not canceling the item withdrawal instead of giving the player the item one tick later back?
- [] We should add a Test to the LobbyManager.isInLobby method it is quite important and used in many places
- [x] Why is the movement of the inventory challenge item the nether start disabled? The same is the case for the rocket. I think you should customize your inventory as you like
- [x] The load method of joining a challenge is still broken. We have to make sure that the world is loaded before we teleport the player
- [x] The levels of the last challenge the user was in gets also given in the lobby. It should only be given in the challenge world
- [x] Once the player joins a challenge he has the correct food level but the saturation level is set to maximum. We should also save it and restore it
- [x] Limit how far the user can travel in the lobby world. Should be teleported back after 500 blocks or so
- [x] Customize Chat appearance. "Player: message". Operators should always be red, normal players gray. Players in the same challenge as you should be green.

## Later Features / Backlog

- Premium users should be able to enable custom beacon effects in the lobby just for them by freely selecting them in a GUI when he clicks on the beacon. The beacon effect should be given to the player when he enters the lobby and removed when he leaves
- Operators should be able to go into the creative mode in the lobby, fly around and build and break blocks. Also interaction should be allowed for them. But only if he is in creative mode. If he switches to survival mode he should be put back into adventure mode and not be able to break or place blocks anymore
- Add pagination for challenge menu

package com.aimobs.entity;

import com.aimobs.ai.AiProvider;
import com.aimobs.ai.AiTask;
import com.aimobs.ai.TaskParser;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The body. A fairly ordinary humanoid mob that owns a small job queue and
 * executes {@link AiTask}s. All the "thinking" happens on the client (offline
 * brain or a real LLM) - this class just does what it is told.
 */
public class AiMobEntity extends PathAwareEntity {

	private static final TrackedData<String> PROVIDER =
			DataTracker.registerData(AiMobEntity.class, TrackedDataHandlerRegistry.STRING);

	/** What the AI is doing when it has no job in the queue. */
	public enum Mode {
		STAY, FOLLOW, GUARD, WANDER
	}

	private final Deque<AiTask> queue = new ArrayDeque<>();
	private AiTask current;
	private Mode mode = Mode.STAY;

	private UUID ownerUuid;
	private int taskTimer;
	private int repathTimer;
	private int danceTimer;
	private BlockPos targetBlock;

	public AiMobEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
		super(entityType, world);
		this.setCustomNameVisible(true);
	}

	// ------------------------------------------------------------------
	// Setup
	// ------------------------------------------------------------------

	public static DefaultAttributeContainer.Builder createAttributes() {
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.33)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 3.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0)
				.add(EntityAttributes.GENERIC_ARMOR, 2.0);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(0, new SwimGoal(this));
		this.goalSelector.add(1, new MeleeAttackGoal(this, 1.15, true));
		this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
		this.goalSelector.add(7, new LookAroundGoal(this));
		this.targetSelector.add(1, new RevengeGoal(this));
	}

	@Override
	protected void initDataTracker() {
		super.initDataTracker();
		this.dataTracker.startTracking(PROVIDER, AiProvider.FREE.getId());
	}

	/** Never despawn - these are your buddies, not wildlife. */
	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}

	// ------------------------------------------------------------------
	// Synced / saved state
	// ------------------------------------------------------------------

	public String getProviderKey() {
		return this.dataTracker.get(PROVIDER);
	}

	public void setProviderKey(String key) {
		this.dataTracker.set(PROVIDER, key == null ? AiProvider.FREE.getId() : key);
	}

	public AiProvider getProvider() {
		return AiProvider.byId(this.getProviderKey());
	}

	public UUID getOwnerUuid() {
		return this.ownerUuid;
	}

	public void setOwner(PlayerEntity player) {
		this.ownerUuid = player.getUuid();
	}

	public boolean isFollowing() {
		return this.mode == Mode.FOLLOW;
	}

	public void setMode(Mode mode) {
		this.mode = mode;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putString("AiProvider", this.getProviderKey());
		nbt.putString("AiMode", this.mode.name());
		if (this.ownerUuid != null) {
			nbt.putUuid("AiOwner", this.ownerUuid);
		}
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("AiProvider")) {
			this.setProviderKey(nbt.getString("AiProvider"));
		}
		if (nbt.contains("AiMode")) {
			try {
				this.mode = Mode.valueOf(nbt.getString("AiMode"));
			} catch (IllegalArgumentException ignored) {
				this.mode = Mode.STAY;
			}
		}
		if (nbt.contains("AiOwner")) {
			UUID owner = nbt.getUuid("AiOwner");
			if (owner != null) {
				this.ownerUuid = owner;
			}
		}
	}

	// ------------------------------------------------------------------
	// Public API used by the server packet handler
	// ------------------------------------------------------------------

	/** Queue whatever the brain decided, and say it out loud. */
	public void onCommands(String say, List<String> commands) {
		if (say != null && !say.isBlank() && this.getWorld() instanceof ServerWorld serverWorld) {
			serverWorld.getServer().getPlayerManager()
					.broadcast(Text.literal("[" + this.getName().getString() + "] " + say.trim()), false);
		}

		if (commands == null || commands.isEmpty()) {
			return;
		}

		List<AiTask> parsed = new ArrayList<>();
		for (String raw : commands) {
			AiTask task = TaskParser.parse(raw);
			if (task != null) {
				parsed.add(task);
			}
		}
		if (parsed.isEmpty()) {
			return;
		}

		// A new instruction always wins over whatever was in progress.
		this.queue.clear();
		this.current = null;
		this.targetBlock = null;
		this.queue.addAll(parsed);
	}

	public String describeState() {
		if (this.current != null) {
			return this.current.describe();
		}
		return switch (this.mode) {
			case FOLLOW -> "following you";
			case GUARD -> "guarding";
			case WANDER -> "exploring";
			case STAY -> "waiting";
		};
	}

	// ------------------------------------------------------------------
	// Tick
	// ------------------------------------------------------------------

	@Override
	public void tick() {
		super.tick();

		if (this.getWorld().isClient()) {
			if (this.danceTimer > 0) {
				this.danceTimer--;
				this.setYaw(this.getYaw() + 22.0f);
			}
			return;
		}

		if (!(this.getWorld() instanceof ServerWorld)) {
			return;
		}

		if (this.current == null && !this.queue.isEmpty()) {
			this.current = this.queue.poll();
			this.taskTimer = 0;
			this.startTask(this.current);
		}

		if (this.current != null) {
			this.tickTask(this.current);
		} else {
			this.tickIdle();
		}

		if (this.danceTimer > 0) {
			this.danceTimer--;
			this.setYaw(this.getYaw() + 22.0f);
		}
	}

	private void startTask(AiTask task) {
		this.targetBlock = null;
		switch (task.kind) {
			case FOLLOW -> this.mode = Mode.FOLLOW;
			case STAY -> {
				this.mode = Mode.STAY;
				this.getNavigation().stop();
				this.setTarget(null);
			}
			case GUARD -> this.mode = Mode.GUARD;
			case WANDER -> this.mode = Mode.WANDER;
			case COME -> {
				PlayerEntity owner = this.getOwner();
				if (owner != null && !owner.isRemoved()) {
					this.getNavigation().startMovingTo(owner, 1.2);
				}
			}
			case GOTO -> {
				if (task.pos != null) {
					this.getNavigation().startMovingTo(task.pos.getX() + 0.5, task.pos.getY(), task.pos.getZ() + 0.5, 1.2);
				}
			}
			case ATTACK -> {
				LivingEntity target = this.findAttackTarget(task.arg);
				if (target != null) {
					this.setTarget(target);
				} else {
					this.say("I can't find any " + task.arg + " nearby.");
					this.finishTask();
				}
			}
			case SAY -> {
				if (task.arg != null && !task.arg.isBlank() && this.getWorld() instanceof ServerWorld serverWorld) {
					serverWorld.getServer().getPlayerManager()
							.broadcast(Text.literal("[" + this.getName().getString() + "] " + task.arg), false);
				}
				this.finishTask();
			}
			case JUMP -> {
				this.getJumpControl().setActive();
				this.finishTask();
			}
			case DANCE -> this.danceTimer = 80;
			case MINE, PLACE -> {
				// Handled in tickTask; nothing to set up here.
			}
		}
	}

	private void tickTask(AiTask task) {
		this.taskTimer++;
		switch (task.kind) {
			case DANCE -> {
				if (this.danceTimer <= 0) {
					this.finishTask();
				}
			}
			case COME -> {
				PlayerEntity owner = this.getOwner();
				boolean arrived = owner != null && this.squaredDistanceTo(owner) < 9.0;
				if (arrived || this.taskTimer > 300) {
					this.finishTask();
				}
			}
			case GOTO -> {
				boolean arrived = task.pos != null
						&& this.getBlockPos().getSquaredDistance(task.pos) < 4.0;
				if (arrived || this.taskTimer > 600) {
					this.getNavigation().stop();
					this.finishTask();
				}
			}
			case ATTACK -> {
				LivingEntity target = this.getTarget();
				if (target == null || !target.isAlive()) {
					LivingEntity next = this.findAttackTarget(task.arg);
					if (next != null) {
						this.setTarget(next);
					} else {
						this.finishTask();
					}
				}
			}
			case MINE -> this.tickMine(task);
			case PLACE -> this.tickPlace(task);
			case FOLLOW, STAY, GUARD, WANDER -> this.finishTask();
			default -> this.finishTask();
		}
	}

	private void finishTask() {
		this.current = null;
		this.taskTimer = 0;
		this.targetBlock = null;
	}

	// ------------------------------------------------------------------
	// Idle behaviour (no queued job)
	// ------------------------------------------------------------------

	private void tickIdle() {
		LivingEntity target = this.getTarget();
		if (target != null && target.isAlive()) {
			return; // let MeleeAttackGoal do its thing
		}

		switch (this.mode) {
			case FOLLOW -> {
				PlayerEntity owner = this.getOwner();
				if (owner == null || owner.isRemoved()) {
					this.mode = Mode.STAY;
					return;
				}
				double distance = this.squaredDistanceTo(owner);
				if (distance > 1024.0) {
					// Owner flew away / teleported: catch up instantly.
					this.teleportTo(owner.getX(), owner.getY(), owner.getZ());
				} else if (distance > 25.0 && --this.repathTimer <= 0) {
					this.repathTimer = 12;
					this.getNavigation().startMovingTo(owner, 1.2);
				}
				this.getLookControl().lookAt(owner, 10.0f, 10.0f);
			}
			case GUARD -> {
				if (--this.repathTimer <= 0) {
					this.repathTimer = 20;
					LivingEntity hostile = this.findNearestHostile(14.0);
					if (hostile != null) {
						this.setTarget(hostile);
					}
				}
			}
			case WANDER -> {
				if (this.getNavigation().isIdle() && this.random.nextInt(60) == 0) {
					double x = this.getX() + (this.random.nextDouble() - 0.5) * 16.0;
					double y = this.getY();
					double z = this.getZ() + (this.random.nextDouble() - 0.5) * 16.0;
					this.getNavigation().startMovingTo(x, y, z, 1.0);
				}
			}
			case STAY -> {
				// Stand still and look around.
			}
		}
	}

	// ------------------------------------------------------------------
	// Mining
	// ------------------------------------------------------------------

	private void tickMine(AiTask task) {
		if (task.count <= 0) {
			this.say("Done. That's all I could find.");
			this.finishTask();
			return;
		}

		if (this.targetBlock == null || !this.isMineable(this.targetBlock) || !TaskParser.matches(
				this.getWorld().getBlockState(this.targetBlock), task.arg)) {
			this.targetBlock = this.findBlock(task.arg, 16, 8);
			if (this.targetBlock == null) {
				this.say("I can't find any " + prettyBlock(task.arg) + " around here.");
				this.finishTask();
				return;
			}
		}

		double distance = this.getBlockPos().getSquaredDistance(this.targetBlock);
		if (distance > 6.0) {
			if (--this.repathTimer <= 0) {
				this.repathTimer = 15;
				this.getNavigation().startMovingTo(
						this.targetBlock.getX() + 0.5,
						this.targetBlock.getY(),
						this.targetBlock.getZ() + 0.5,
						1.2);
			}
			return;
		}

		this.getNavigation().stop();
		this.getLookControl().lookAt(
				this.targetBlock.getX() + 0.5,
				this.targetBlock.getY() + 0.5,
				this.targetBlock.getZ() + 0.5);

		if (this.taskTimer % 10 == 0) {
			if (this.getWorld() instanceof ServerWorld serverWorld) {
				serverWorld.breakBlock(this.targetBlock, true, this);
			}
			this.targetBlock = null;
			task.count--;
			if (task.count <= 0) {
				this.finishTask();
			}
		}
	}

	private boolean isMineable(BlockPos pos) {
		BlockState state = this.getWorld().getBlockState(pos);
		if (state.isAir() || !state.getFluidState().isEmpty()) {
			return false;
		}
		return state.getHardness(this.getWorld(), pos) >= 0.0f;
	}

	private BlockPos findBlock(String spec, int horizontal, int vertical) {
		BlockPos origin = this.getBlockPos();
		BlockPos min = origin.add(-horizontal, -vertical, -horizontal);
		BlockPos max = origin.add(horizontal, vertical, horizontal);

		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;

		for (BlockPos pos : BlockPos.iterate(min, max)) {
			BlockState state = this.getWorld().getBlockState(pos);
			if (state.isAir() || !state.getFluidState().isEmpty()) {
				continue;
			}
			if (!TaskParser.matches(state, spec)) {
				continue;
			}
			if (!this.isMineable(pos)) {
				continue;
			}
			double distance = origin.getSquaredDistance(pos);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = pos.toImmutable();
			}
		}
		return best;
	}

	// ------------------------------------------------------------------
	// Placing
	// ------------------------------------------------------------------

	private void tickPlace(AiTask task) {
		if (task.count <= 0) {
			this.finishTask();
			return;
		}
		if (this.taskTimer % 6 != 0) {
			return;
		}

		BlockPos placeAt = this.findPlacePos();
		if (placeAt != null && this.getWorld() instanceof ServerWorld) {
			Block block = TaskParser.resolveBlock(task.arg);
			this.getWorld().setBlockState(placeAt, block.getDefaultState(), 3);
			task.count--;
		} else if (this.taskTimer > 120) {
			this.say("I can't find anywhere to put that.");
			task.count = 0;
		}

		if (task.count <= 0) {
			this.finishTask();
		}
	}

	private BlockPos findPlacePos() {
		BlockPos origin = this.getBlockPos();
		Vec3d look = this.getRotationVec(1.0f);
		for (int step = 2; step <= 4; step++) {
			int x = origin.getX() + (int) Math.round(look.x * step);
			int z = origin.getZ() + (int) Math.round(look.z * step);
			for (int dy = -1; dy <= 1; dy++) {
				BlockPos candidate = new BlockPos(x, origin.getY() + dy, z);
				if (!this.getWorld().getBlockState(candidate).isAir()) {
					continue;
				}
				if (!this.getWorld().getBlockState(candidate.down()).isAir()) {
					return candidate;
				}
			}
		}
		return null;
	}

	// ------------------------------------------------------------------
	// Targeting helpers
	// ------------------------------------------------------------------

	private LivingEntity findAttackTarget(String spec) {
		Box box = this.getBoundingBox().expand(24.0);
		List<LivingEntity> candidates = this.getWorld()
				.getEntitiesByClass(LivingEntity.class, box, e -> e != this && e.isAlive() && this.matchesTarget(e, spec));

		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (LivingEntity candidate : candidates) {
			if (this.ownerUuid != null && this.ownerUuid.equals(candidate.getUuid())) {
				continue;
			}
			if (candidate instanceof AiMobEntity) {
				continue;
			}
			double distance = this.squaredDistanceTo(candidate);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = candidate;
			}
		}
		return best;
	}

	private boolean matchesTarget(LivingEntity entity, String spec) {
		String s = spec == null ? "monster" : spec.toLowerCase(Locale.ROOT).trim();
		switch (s) {
			case "monster", "hostile", "enemy", "mobs", "anything":
				return entity instanceof Monster;
			case "animal", "passive", "animals":
				return entity instanceof AnimalEntity;
			case "player", "players":
				return entity instanceof PlayerEntity;
			default:
				String path = Registries.ENTITY_TYPE.getId(entity.getType()).getPath();
				return path.equals(s) || path.equals("minecraft:" + s) || path.contains(s);
		}
	}

	private LivingEntity findNearestHostile(double radius) {
		Box box = this.getBoundingBox().expand(radius);
		List<LivingEntity> hostiles = this.getWorld()
				.getEntitiesByClass(LivingEntity.class, box,
						e -> e != this && e.isAlive() && e instanceof Monster && !(e instanceof AiMobEntity));

		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (LivingEntity hostile : hostiles) {
			double distance = this.squaredDistanceTo(hostile);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = hostile;
			}
		}
		return best;
	}

	public PlayerEntity getOwner() {
		if (this.ownerUuid == null) {
			return null;
		}
		if (this.getServer() == null) {
			return null;
		}
		return this.getServer().getPlayerManager().getPlayer(this.ownerUuid);
	}

	private void say(String message) {
		if (this.getWorld() instanceof ServerWorld serverWorld) {
			serverWorld.getServer().getPlayerManager()
					.broadcast(Text.literal("[" + this.getName().getString() + "] " + message), false);
		}
	}

	private static String prettyBlock(String spec) {
		String s = spec == null ? "blocks" : spec;
		if (s.startsWith("#")) {
			return s.substring(1);
		}
		int colon = s.indexOf(':');
		if (colon >= 0) {
			s = s.substring(colon + 1);
		}
		return s.replace('_', ' ');
	}
}

import { mutation } from "./_generated/server";
import { v } from "convex/values";

/**
 * Creates a new user with email and password.
 */
export const create = mutation({
    args: {
        name: v.string(),
        email: v.string(),
        password: v.string(),
    },
    handler: async (ctx, args) => {
        // Check if user already exists
        const existingUser = await ctx.db
            .query("users")
            .withIndex("by_email", (q) => q.eq("email", args.email))
            .first();

        if (existingUser) {
            throw new Error("User with this email already exists");
        }

        const userId = await ctx.db.insert("users", {
            email: args.email,
            password: args.password,
            region: "eu", // Default region
            createdAt: Date.now(),
            lastLogin: Date.now(),
            // name is not in schema but could be stored if schema was updated. 
            // For now we persist standard fields. 
            // If name is important, we should add it to schema.js, 
            // but for now relying on existing schema + password.
        });

        // Create default config
        await ctx.db.insert("configs", {
            userId,
            deviceId: "",
            minBattery: 20,
            maxBattery: 80,
            checkInterval: 300,
            emailNotifications: true,
            totalCharges: 0,
        });

        return userId;
    },
});


export const updateUsername = mutation({
    args: { userId: v.id("users"), newName: v.string() },
    handler: async (ctx, { userId, newName }) => {
        const user = await ctx.db.get(userId);
        if (!user) throw new Error("User not found");

        const count = user.nameChangeCount || 0;
        if (count >= 5) {
            throw new Error("Maximum name changes reached (5)");
        }

        // Check uniqueness
        const existing = await ctx.db.query("users").withIndex("by_name", q => q.eq("name", newName)).first();
        if (existing) throw new Error("Username already taken");

        await ctx.db.patch(userId, {
            name: newName,
            nameChangeCount: count + 1
        });

        return { success: true };
    }
});

export const generateUploadUrl = mutation({
    args: {},
    handler: async (ctx) => {
        return await ctx.storage.generateUploadUrl();
    },
});

export const updateProfileImage = mutation({
    args: { userId: v.id("users"), storageId: v.id("_storage") },
    handler: async (ctx, args) => {
        const url = await ctx.storage.getUrl(args.storageId);
        await ctx.db.patch(args.userId, { pictureUrl: url });
        return url;
    },
});

export const removeProfileImage = mutation({
    args: { userId: v.id("users") },
    handler: async (ctx, args) => {
        // Generate default image based on name
        const user = await ctx.db.get(args.userId);
        const name = user.name || user.email.split('@')[0];
        const defaultUrl = `https://ui-avatars.com/api/?name=${name}&background=random&color=fff&size=200`;

        await ctx.db.patch(args.userId, { pictureUrl: defaultUrl });
        return defaultUrl;
    }
});

export const setPassword = mutation({
    args: { userId: v.id("users"), passwordHash: v.string() },
    handler: async (ctx, { userId, passwordHash }) => {
        await ctx.db.patch(userId, { password: passwordHash });
    }
});

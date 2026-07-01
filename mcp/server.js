#!/usr/bin/env node

/**
 * Bus-Hop MCP Server
 *
 * Gradle build/test/lint automation for the BusHop Android project.
 *
 * Tools:
 *   gradle_tasks         — list available Gradle tasks
 *   gradle_test          — run unit tests
 *   gradle_build_debug   — assemble debug APK + verify
 *   gradle_build_release — assemble release APK
 *   gradle_clean_build   — full clean → test → APK pipeline
 *   gradle_update_badges — refresh shields.io badges
 *   gradle_lint          — run Android lint
 */

import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { spawnSync } from "child_process";
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from "@modelcontextprotocol/sdk/types.js";

const PROJECT_DIR = "/home/nami/projects/dev/Bus-Hop";
const GRADLEW = `${PROJECT_DIR}/gradlew`;

function run(args, opts = {}) {
  const result = spawnSync(GRADLEW, args, {
    cwd: PROJECT_DIR,
    maxBuffer: 2 * 1024 * 1024,
    encoding: "utf8",
    ...opts,
  });
  const stdout = (result.stdout ?? "").trim();
  const stderr = (result.stderr ?? "").trim();
  const output = stdout || stderr || `exit code ${result.status}`;
  return { stdout, stderr, output, status: result.status };
}

const server = new Server(
  { name: "bus-hop", version: "1.0.0" },
  { capabilities: { tools: {} } },
);

server.setRequestHandler(ListToolsRequestSchema, async () => ({
  tools: [
    {
      name: "gradle_tasks",
      description: "List all available Gradle tasks in the Bus-Hop project",
      inputSchema: {
        type: "object",
        properties: {
          group: {
            type: "string",
            description: "Filter tasks by group (e.g., 'build', 'verification', 'development')",
          },
        },
      },
    },
    {
      name: "gradle_test",
      description: "Run unit tests across all modules (domain, data, app). 161+ tests covering ViewModels, use cases, search index, refresh logic, architecture constraints.",
      inputSchema: {
        type: "object",
        properties: {},
      },
    },
    {
      name: "gradle_build_debug",
      description: "Assemble debug APK with R8 minification, then verify APK integrity (AndroidManifest check, size gate). Output: app/build/outputs/apk/debug/bus-hop.apk",
      inputSchema: {
        type: "object",
        properties: {},
      },
    },
    {
      name: "gradle_build_release",
      description: "Assemble release APK with full R8 + shrinkResources. Needs RELEASE_KEYSTORE env vars for signing. Output: app/build/outputs/apk/release/",
      inputSchema: {
        type: "object",
        properties: {},
      },
    },
    {
      name: "gradle_clean_build",
      description: "Full clean → test → debug APK pipeline: clean, test, assembleDebug, checkAndRenameDebugApk. This is the standard development loop.",
      inputSchema: {
        type: "object",
        properties: {},
      },
    },
    {
      name: "gradle_update_badges",
      description: "Refresh static badge SVGs in docs/badges/ from shields.io. Auto-detects current test count from test results.",
      inputSchema: {
        type: "object",
        properties: {
          testCount: {
            type: "string",
            description: "Explicit test count (omit to auto-detect from latest test results)",
          },
        },
      },
    },
    {
      name: "gradle_lint",
      description: "Run Android lint checks across all modules. Reports in app/build/reports/lint-results-*.html",
      inputSchema: {
        type: "object",
        properties: {},
      },
    },
  ],
}));

server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const { name, arguments: args } = request.params;

  switch (name) {
    case "gradle_tasks": {
      const gradleArgs = ["tasks"];
      if (args?.group) gradleArgs.push("--group", args.group);
      const result = run(gradleArgs, { maxBuffer: 4 * 1024 * 1024 });
      if (result.status !== 0) {
        return { content: [{ type: "text", text: `❌ gradle tasks failed:\n${result.output}` }] };
      }
      return { content: [{ type: "text", text: result.output }] };
    }

    case "gradle_test": {
      const result = run(["test"]);
      if (result.status !== 0) {
        return { content: [{ type: "text", text: `❌ Tests failed:\n${result.output}` }] };
      }
      // Extract test summary
      const passMatch = result.stdout.match(/(\d+) tests? completed/i);
      const failMatch = result.stdout.match(/(\d+) failures?/i);
      return {
        content: [{
          type: "text",
          text: `✅ Tests passed\n${passMatch ? passMatch[0] : ""}${failMatch ? `\n${failMatch[0]}` : ""}\n\nFull output:\n${result.output}`,
        }],
      };
    }

    case "gradle_build_debug": {
      const result = run(["assembleDebug"]);
      if (result.status !== 0) {
        return { content: [{ type: "text", text: `❌ Debug build failed:\n${result.output}` }] };
      }
      // APK verification is chained via finalizedBy
      return {
        content: [{
          type: "text",
          text: `✅ Debug APK built\n${result.output}`,
        }],
      };
    }

    case "gradle_build_release": {
      const result = run(["assembleRelease"]);
      if (result.status !== 0) {
        return { content: [{ type: "text", text: `❌ Release build failed:\n${result.output}` }] };
      }
      return {
        content: [{
          type: "text",
          text: `✅ Release APK built\n${result.output}`,
        }],
      };
    }

    case "gradle_clean_build": {
      const result = run(["clean", "test", "assembleDebug"]);
      if (result.status !== 0) {
        return { content: [{ type: "text", text: `❌ Clean build failed:\n${result.output}` }] };
      }
      return {
        content: [{
          type: "text",
          text: `✅ Clean → test → build passed\n${result.output}`,
        }],
      };
    }

    case "gradle_update_badges": {
      const gradleArgs = ["updateBadges"];
      if (args?.testCount) {
        gradleArgs.push("-PtestCount", args.testCount);
      } else {
        gradleArgs.push("-PautoDetect");
      }
      const result = run(gradleArgs);
      if (result.status !== 0) {
        return { content: [{ type: "text", text: `❌ Badge update failed:\n${result.output}` }] };
      }
      return {
        content: [{
          type: "text",
          text: `✅ Badges updated\n${result.output}`,
        }],
      };
    }

    case "gradle_lint": {
      const result = run(["lint"]);
      if (result.status !== 0) {
        return { content: [{ type: "text", text: `❌ Lint failed:\n${result.output}` }] };
      }
      return {
        content: [{
          type: "text",
          text: `✅ Lint passed\n${result.output}`,
        }],
      };
    }

    default:
      return { content: [{ type: "text", text: `Unknown tool: ${name}` }] };
  }
});

// Start
const transport = new StdioServerTransport();
await server.connect(transport);

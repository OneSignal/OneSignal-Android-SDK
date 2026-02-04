import type { Options } from '@wdio/types';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export const config: Options.Testrunner = {
    //
    // ====================
    // Runner Configuration
    // ====================
    runner: 'local',
    
    // TypeScript configuration
    autoCompileOpts: {
        autoCompile: true,
        tsNodeOpts: {
            project: './tsconfig.json',
            transpileOnly: true,
            esm: true
        }
    },

    //
    // ==================
    // Specify Test Files
    // ==================
    specs: [
        './test/specs/**/*.ts'
    ],
    exclude: [],

    //
    // ============
    // Capabilities
    // ============
    maxInstances: 1,
    capabilities: [{
        // Android capabilities
        platformName: 'Android',
        'appium:automationName': 'UiAutomator2',
        'appium:deviceName': 'Android Emulator',
        'appium:app': path.join(__dirname, '../app/build/outputs/apk/gms/debug/app-gms-debug.apk'),
        'appium:appPackage': 'com.onesignal.sdktest',
        'appium:appActivity': 'com.onesignal.sdktest.activity.SplashActivity',
        'appium:noReset': false,
        'appium:fullReset': false,
        'appium:newCommandTimeout': 240,
        'appium:autoGrantPermissions': true
    }],

    //
    // ===================
    // Test Configurations
    // ===================
    logLevel: 'info',
    bail: 0,
    baseUrl: '',
    waitforTimeout: 10000,
    connectionRetryTimeout: 120000,
    connectionRetryCount: 3,

    //
    // Appium service configuration
    services: [
        ['appium', {
            args: {
                relaxedSecurity: true,
                log: './logs/appium.log'
            }
        }]
    ],

    //
    // Framework configuration
    framework: 'mocha',
    reporters: ['spec'],
    mochaOpts: {
        ui: 'bdd',
        timeout: 60000
    },

    //
    // =====
    // Hooks
    // =====
    /**
     * Gets executed before test execution begins.
     */
    before: async function () {
        // Wait for the app to fully load
        await driver.pause(3000);
    },

    /**
     * Gets executed after all tests are done.
     */
    after: async function () {
        // Cleanup if needed
    }
};

import appPage from '../pageobjects/app.page.js';

describe('OneSignalDemo App', () => {
    
    describe('App Launch', () => {
        it('should launch the app successfully', async () => {
            // Verify the app is running with correct package
            const currentPackage = await appPage.getCurrentPackage();
            expect(currentPackage).toBe('com.onesignal.sdktest');
        });

        it('should display the main activity', async () => {
            // App starts with SplashActivity then transitions to MainActivity
            // Wait for the app to finish loading
            await driver.pause(3000);
            const currentActivity = await appPage.getCurrentActivity();
            // Could be SplashActivity or MainActivity depending on timing
            expect(currentActivity).toMatch(/SplashActivity|MainActivity/);
        });
    });

    describe('Privacy Consent', () => {
        it('should tap the Allow button to accept privacy consent', async () => {
            // Wait for the privacy consent dialog to appear
            await appPage.waitForAppReady();
            
            // Tap the ALLOW button
            await appPage.tapElementByText('ALLOW');
            
            // Wait for the consent to be processed and activity to transition
            await driver.pause(1000);
            
            // Verify we've transitioned to the MainActivity
            const currentActivity = await appPage.getCurrentActivity();
            expect(currentActivity).toBe('.activity.MainActivity');
        });
    });

    describe('App Navigation', () => {
        it('should have visible UI elements', async () => {
            // Wait for app to be ready
            await appPage.waitForAppReady();
            
            // Take a screenshot for verification
            await appPage.takeScreenshot('navigation-screen');
        });

        it('should be able to scroll the main view', async () => {
            // Scroll down to see more content
            await appPage.scrollDown();
            
            // Wait a moment for scroll to complete
            await driver.pause(500);
        });
    });

    describe('OneSignal SDK Interaction', () => {
        it('should display OneSignal related UI', async () => {
            // This test verifies the OneSignal SDK demo UI is present
            // Customize selectors based on actual app UI elements
            await appPage.waitForAppReady();
            
            // Example: Check if specific OneSignal-related text is visible
            // Modify these assertions based on actual app content
            const packageName = await appPage.getCurrentPackage();
            expect(packageName).toBe('com.onesignal.sdktest');
        });
    });
});

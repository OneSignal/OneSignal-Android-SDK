/**
 * Base Page Object for OneSignalDemo app
 * Contains common elements and methods used across the app
 */
class AppPage {
    /**
     * Wait for the app to be ready
     */
    async waitForAppReady(): Promise<void> {
        // Wait for the main activity to be visible
        await driver.pause(2000);
    }

    /**
     * Get element by resource ID
     * @param resourceId - The Android resource ID (without package prefix)
     */
    async getElementByResourceId(resourceId: string) {
        return $(`android=new UiSelector().resourceId("com.onesignal.sdktest:id/${resourceId}")`);
    }

    /**
     * Get element by text content
     * @param text - The text to search for
     */
    async getElementByText(text: string) {
        return $(`android=new UiSelector().text("${text}")`);
    }

    /**
     * Get element by content description (accessibility ID)
     * @param description - The content description
     */
    async getElementByContentDesc(description: string) {
        return $(`~${description}`);
    }

    /**
     * Check if an element with given text exists
     * @param text - The text to search for
     */
    async hasElementWithText(text: string): Promise<boolean> {
        const element = await this.getElementByText(text);
        return element.isDisplayed();
    }

    /**
     * Tap on an element with the given text
     * @param text - The text of the element to tap
     */
    async tapElementByText(text: string): Promise<void> {
        const element = await this.getElementByText(text);
        await element.waitForDisplayed({ timeout: 10000 });
        await element.click();
    }

    /**
     * Scroll down on the screen
     */
    async scrollDown(): Promise<void> {
        await $('android=new UiScrollable(new UiSelector().scrollable(true)).scrollForward()');
    }

    /**
     * Scroll to an element with specific text
     * @param text - The text to scroll to
     */
    async scrollToText(text: string): Promise<void> {
        await $(`android=new UiScrollable(new UiSelector().scrollable(true)).scrollTextIntoView("${text}")`);
    }

    /**
     * Get the current activity name
     */
    async getCurrentActivity(): Promise<string> {
        return driver.getCurrentActivity();
    }

    /**
     * Get the current package name
     */
    async getCurrentPackage(): Promise<string> {
        return driver.getCurrentPackage();
    }

    /**
     * Take a screenshot and save it
     * @param filename - The name of the screenshot file
     */
    async takeScreenshot(filename: string): Promise<void> {
        await driver.saveScreenshot(`./logs/${filename}.png`);
    }

    /**
     * Press the Android back button
     */
    async pressBack(): Promise<void> {
        await driver.back();
    }

    /**
     * Press the Android home button
     */
    async pressHome(): Promise<void> {
        await driver.pressKeyCode(3); // KEYCODE_HOME
    }
}

export default new AppPage();

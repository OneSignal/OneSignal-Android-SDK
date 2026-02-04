/**
 * Base Page Object for OneSignalDemo app
 * Contains common elements and methods used across the app
 */
class AppPage {
    /**
     * Wait for the app to be ready
     */
    async waitForAppReady(): Promise<void> {
        // Wait for the app to be visible
        await driver.pause(2000);
    }

    /**
     * Get element by resource ID (cross-platform)
     * @param resourceId - The resource ID (without package prefix)
     */
    async getElementByResourceId(resourceId: string) {
        return $(`~${resourceId}`);
    }

    /**
     * Get element by text content (cross-platform using XPath)
     * @param text - The text to search for
     */
    async getElementByText(text: string) {
        return $(`//*[@text="${text}" or @label="${text}" or @value="${text}"]`);
    }

    /**
     * Get element by accessibility ID (cross-platform)
     * @param accessibilityId - The accessibility ID / content description
     */
    async getElementByAccessibilityId(accessibilityId: string) {
        return $(`~${accessibilityId}`);
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
     * Scroll down on the screen (cross-platform using gesture)
     */
    async scrollDown(): Promise<void> {
        const { width, height } = await driver.getWindowSize();
        await driver.action('pointer', { parameters: { pointerType: 'touch' } })
            .move({ x: Math.floor(width / 2), y: Math.floor(height * 0.7) })
            .down()
            .move({ x: Math.floor(width / 2), y: Math.floor(height * 0.3), duration: 300 })
            .up()
            .perform();
    }

    /**
     * Scroll to an element with specific text
     * @param text - The text to scroll to
     * @param maxScrolls - Maximum number of scroll attempts
     */
    async scrollToText(text: string, maxScrolls: number = 5): Promise<void> {
        for (let i = 0; i < maxScrolls; i++) {
            const element = await this.getElementByText(text);
            if (await element.isDisplayed().catch(() => false)) {
                return;
            }
            await this.scrollDown();
        }
    }

    /**
     * Take a screenshot and save it
     * @param filename - The name of the screenshot file
     */
    async takeScreenshot(filename: string): Promise<void> {
        await driver.saveScreenshot(`./logs/${filename}.png`);
    }

    /**
     * Press the back button (cross-platform)
     */
    async pressBack(): Promise<void> {
        await driver.back();
    }

    /**
     * Navigate to home screen (cross-platform)
     */
    async pressHome(): Promise<void> {
        if (driver.isAndroid) {
            await driver.pressKeyCode(3); // KEYCODE_HOME
        } else {
            // iOS doesn't support programmatic home press in the same way
            await driver.execute('mobile: pressButton', { name: 'home' });
        }
    }
}

export default new AppPage();

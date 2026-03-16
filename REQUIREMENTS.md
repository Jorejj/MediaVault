1.  Supporting Multiple Screens

    Instruction:
    Observe the actual behavior of the application on multiple emulators or devices.

    1.1 Dealing with Android Market Fragmentation - Screen resolutions & densities (mdpi, hdpi, xhdpi, etc.) - Android OS versions

    1.2 Creating Drawable Resources for Multiple Screens - PNG / JPG - Icons - Vector graphics - Shapes - Backgrounds

    1.3 Creating Stretchable 9-Patch Graphics - 9-Patch image - File name format: .9.png

    1.4 Creating a Custom Launcher Icon - App icon displayed on the home screen

2.  Managing the User Interface

    2.1 Defining and Using Styles - Located in: res/values/styles

    2.2 Applying Application Themes - Located in: res/values/themes

    2.3 Creating a Scrollable Text Display - Implemented inside Fragment layout

    2.4 Laying Out a Screen with Fragments - Placeholder for fragments - Static fragments - Dynamic fragments - Fragment-based UI for navigation

3.  Working with Events

    3.1 Handling User Events with Java Code - Text input events - setOnClickListener() - Validation checks

    3.2 Creating a Broadcast Receiver to Handle System Events - Create inside the Java package folder - Must be registered in the Manifest

        Location:
        app/src/main/AndroidManifest.xml

        Purpose:
        - Handles system events

    3.3 Handling Orientation and Other Configuration Changes - Orientation changes (portrait ↔ landscape) - Usually handled inside the Activity class - Control configuration changes in the manifest file - Use separate layouts for different orientations through alternative layouts

4.  Working with Menus and the Action Bar

    4.1 Adding Items to the Options Menu
    Location:
    app/src/main/res/menu/

    4.2 Displaying Menu Items in the Action Bar - Menu is loaded inside the Java Activity file - Icons are configured inside the menu XML - Ensure `showAsAction` is set

    4.3 Managing the Action Bar and Menus at Runtime - Menu behavior controlled inside the Activity Java file - Show/hide menu items dynamically

        Files:
        - MainActivity.java → onCreateOptionsMenu()
        - MainActivity.java → onOptionsItemSelected()

5.  Working with Data

    5.1 Passing Data to an Activity with Intent Extras - Implemented inside the Java Activity files - Data sent using Intent

    5.2 Receiving Data in a New Activity - Demonstrates screen-to-screen data transfer

    5.3 Returning Data to a Calling Activity - Sending data back to the previous screen

    5.4 Displaying Data in a List - Show multiple data items

    5.5 Handling List Item Click Events - Detect which item the user clicked

    5.6 Customizing the List Item - Custom layout instead of default list layout

    5.7 Exploring Other Uses of Data - Additional ways activities can use passed data

6.  Working with Dynamic Data using SQLite

    6.1 Fetching Data from Database

    6.2 Understanding Different SQL Queries

    6.3 Creating a CRUD Application using SQLite Database

# My Application

This Application connects to a crownstone, subscribes to the mesh characteristic, and uploads all received
scans to the crownstone cloud.

##Installation

The project depends on bluenet-lib-android and crownstone-loopback-sdk. To install follow these steps:

1. Clone this project to your disk

        git clone https://github.com/dobots/crownstone-hub

2. Clone the two libraries into the project location

        cd path/to/project/location
        git clone https://github.com/dobots/bluenet-lib-android.git bluenet-lib
        git clone https://github.com/dobots/crownstone-loopback-sdk.git crownstone-loopback-sdk

    Make sure the folders of the libraries will be called bluenet-lib and crownstone-loopback-sdk

3. Import the project in Android Studio

        File > New > Import Project ...

5. Build and run

##Copyrights

The copyrights (2015) for this code belongs to [DoBots](http://dobots.nl) and are provided under an noncontagious open-source license:

* Author: Dominik Egger
* Date: 17.12.2015
* License: LGPL v3
* Distributed Organisms B.V. (DoBots), http://www.dobots.nl
* Rotterdam, The Netherlands

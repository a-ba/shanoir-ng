const webpack = require('webpack');
const webpackMerge = require('webpack-merge');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const ExtractTextPlugin = require('extract-text-webpack-plugin');
const CompressionWebpackPlugin = require("compression-webpack-plugin");
const commonConfig = require('./webpack.common.js');
const helpers = require('./helpers');
const AotPlugin = require('@ngtools/webpack').AotPlugin;

/**
 * Webpack Constants
 */
const ENV = process.env.ENV = process.env.NODE_ENV = 'production';
const BACKEND_API_ROOT_URL = 'SHANOIR_SERVER_URL/shanoir-ng';
const METADATA = webpackMerge(commonConfig.metadata, {
    host: 'localhost',
    BACKEND_API_USERS_MS_URL: BACKEND_API_ROOT_URL + '/users',
    BACKEND_API_STUDIES_MS_URL: BACKEND_API_ROOT_URL + '/studies',
    KEYCLOAK_BASE_URL: 'SHANOIR_SERVER_URL/auth',
    LOGOUT_REDIRECT_URL: 'SHANOIR_SERVER_URL/shanoir-ng/index.html',
    port: 8080,
    ENV: ENV,
});

module.exports = webpackMerge(commonConfig, {
    devtool: 'source-map',

    output: {
        path: helpers.root('dist'),
        publicPath: '/shanoir-ng/',
        filename: '[name].[hash].js',
        chunkFilename: '[id].[hash].chunk.js'
    },

    module: {
        rules: [
            {
                test: /\.ts$/,
                loaders: ['@ngtools/webpack']
            },
            /**
             * File loader for supporting images, for example, in CSS files.
             */
            {
                test: /\.(jpg|png|gif)$/,
                loader: 'file-loader'
           }
        ]
    },

    plugins: [
        new AotPlugin({
            tsConfigPath: './tsconfig-aot.json',
            entryModule: helpers.root('src/app/app.module#AppModule')
        }),

        new HtmlWebpackPlugin({
            filename: 'index.html',
            template: 'src/index-qualif.html',
            inject: true  
        }),

        new webpack.NoEmitOnErrorsPlugin(),

        new webpack.optimize.UglifyJsPlugin({ // https://github.com/angular/angular/issues/10618
            "compress": { "warnings": false },
            "sourceMap": false,
            "comments": false,
            "mangle": true,
            "minimize": true
        }),

        new ExtractTextPlugin('[name].[hash].css'),
        
        new webpack.LoaderOptionsPlugin({
            htmlLoader: {
                minimize: false // workaround for ng2
            }
        }),

        // GZIP the files
        new CompressionWebpackPlugin({
            asset: "[path].gz[query]",
            algorithm: "gzip",
            test: new RegExp(
                "\\.(" + ["js", "css"].join("|") +")$"
            ),
            threshold: 10240,
            minRatio: 0.8
        }),

        /**
         * Plugin: DefinePlugin
         * Description: Define free variables.
         * Useful for having development builds with debug logging or adding global constants.
         *
         * Environment helpers
         *
         * See: https://webpack.github.io/docs/list-of-plugins.html#defineplugin
         */
        new webpack.DefinePlugin({
            'ENV': JSON.stringify(METADATA.ENV),
            'BACKEND_API_USERS_MS_URL': JSON.stringify(METADATA.BACKEND_API_USERS_MS_URL),
            'BACKEND_API_STUDIES_MS_URL': JSON.stringify(METADATA.BACKEND_API_STUDIES_MS_URL),
            'KEYCLOAK_BASE_URL': JSON.stringify(METADATA.KEYCLOAK_BASE_URL),
            'LOGOUT_REDIRECT_URL': JSON.stringify(METADATA.LOGOUT_REDIRECT_URL),
            'process.env': {
                'ENV': JSON.stringify(METADATA.ENV),
                'NODE_ENV': JSON.stringify(METADATA.ENV),
                'BACKEND_API_USERS_MS_URL': JSON.stringify(METADATA.BACKEND_API_USERS_MS_URL),
                'BACKEND_API_STUDIES_MS_URL': JSON.stringify(METADATA.BACKEND_API_STUDIES_MS_URL),
                'LOGOUT_REDIRECT_URL': JSON.stringify(METADATA.LOGOUT_REDIRECT_URL),
                'KEYCLOAK_BASE_URL': JSON.stringify(METADATA.KEYCLOAK_BASE_URL),
            }
        })
    ]
});
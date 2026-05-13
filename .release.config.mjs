export default {
    branches: ['main'],

    plugins: [
        [
            '@semantic-release/commit-analyzer',
            {
                preset: 'conventionalcommits',
                releaseRules: [
                    { type: 'feat', release: 'minor' },
                    { type: 'fix', release: 'patch' },
                    { type: 'perf', release: 'patch' },

                    { type: 'docs', release: false },
                    { type: 'chore', release: false },
                    { type: 'refactor', release: false },
                    { type: 'test', release: false },
                    { type: 'ci', release: false },
                    { type: 'build', release: false },
                ],
            },
        ],

        [
            '@semantic-release/release-notes-generator',
            {
                preset: 'conventionalcommits',
                presetConfig: {
                    types: [
                        {
                            type: 'feat',
                            section: '✨ Features',
                        },
                        {
                            type: 'fix',
                            section: '🐛 Fixes',
                        },
                        {
                            type: 'perf',
                            section: '⚡ Performance',
                        },
                    ],
                },
            },
        ],

        [
            '@semantic-release/changelog',
            {
                changelogFile: 'CHANGELOG.md',
            },
        ],

        '@semantic-release/github',

        [
            '@semantic-release/git',
            {
                assets: ['CHANGELOG.md'],
                message:
                    'chore(release): ${nextRelease.version} [skip ci]\n\n${nextRelease.notes}',
            },
        ],
    ],
};

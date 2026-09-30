import {defineConfig} from "@playwright/test";

export default defineConfig({
  testDir:"./tests",
  fullyParallel:false,
  timeout:process.env.CI?60_000:30_000,
  expect:{timeout:process.env.CI?15_000:5_000},
  workers:process.env.CI?2:undefined,
  use:{
    baseURL:"http://127.0.0.1:8080",
    trace:"retain-on-failure",
    actionTimeout:process.env.CI?20_000:0,
    navigationTimeout:process.env.CI?30_000:0
  },
  webServer:{
    command:"php -S 127.0.0.1:8080 -t public",
    url:"http://127.0.0.1:8080",
    reuseExistingServer:!process.env.CI
  },
  projects:[
    {name:"mobile-375",use:{viewport:{width:375,height:812}}},
    {name:"mobile-390",use:{viewport:{width:390,height:844}}},
    {name:"tablet",use:{viewport:{width:768,height:1024}}},
    {name:"desktop-1440",use:{viewport:{width:1440,height:1000}}},
    {name:"desktop-1920",use:{viewport:{width:1920,height:1080}}}
  ]
});

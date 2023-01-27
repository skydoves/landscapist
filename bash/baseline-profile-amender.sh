# Designed and developed by 2020-2023 skydoves (Jaewoong Eum)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

awk '/glide/' app/src/main/baseline-prof.txt > glide/src/main/baseline-prof.txt
awk '/fresco|Fresco/' app/src/main/baseline-prof.txt > fresco/src/main/baseline-prof.txt
awk '/coil/' app/src/main/baseline-prof.txt > coil/src/main/baseline-prof.txt
awk '/landscapist/' app/src/main/baseline-prof.txt > landscapist/src/main/baseline-prof.txt
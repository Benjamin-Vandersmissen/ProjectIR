#include <iostream>
#include "tinyXML/tinyxml2.h"
#include <fstream>
#include <algorithm>
#include <vector>
#include <map>
#include <filesystem>

std::map<std::size_t, std::ofstream> streams;

bool reset = false;

std::ofstream& get_stream(std::size_t index){
    if (streams.find(index) != streams.end()){
        return streams[index];
    }
    else{
        if (streams.size() > 500){
            reset = true;
            for (auto& pair : streams){
                pair.second.close();
            }
			streams.clear();
        }
        streams.emplace(index, std::ofstream("data/" + std::to_string(index) + ".xml", std::ostream::app));
        return streams[index];
    }
}

/*
 * 0 = file bestaat niet
 * 1 = file bestaat, maar geen entry
 * 2 = file bestaat en entry
 * */

int contains(std::size_t parent, std::string index){
    if(std::filesystem::exists("data/" + std::to_string(parent)+".xml")){
        std::ifstream file("data/" + std::to_string(parent) + ".xml");
        std::string s;
        while(std::getline(file, s)){
            if (s.find(index) != std::string::npos){
                std::cerr << "TEST" << std::endl;
                return 2;
            }
        }
        return 1;
    }
    return 0;
}

int main() {
//    std::ifstream file("Posts.xml");
//    std::string s;
//    std::getline(file, s);
//    int a = 0;
//    std::vector<std::size_t> ids;
//    std::size_t current_id = 50258653;
//    std::size_t id = 0;
//    std::getline(file, s);
//    while (id <= current_id){
//        std::getline(file, s);
//        auto loc = s.find("Id=\"") + 4;
//        id = std::stoul(s.substr(loc, s.find('\"', loc) - loc));
//        a++;
//    }
//    std::size_t limit = a + 5000;
//    while(std::getline(file, s)) {
//        if (s.find("PostTypeId=\"1\"") != std::string::npos) {
//            auto tagpos = s.find("Tags=\"");
//            if (s.find("python", tagpos) != std::string::npos or s.find("c++", tagpos) != std::string::npos) {
//                auto loc = s.find("Id=\"") + 4;
//                auto id = std::stoul(s.substr(loc, s.find('\"', loc) - loc));
//                ids.push_back(id);
//                get_stream(id) << s << '\n';
//            }
//        } else if (s.find("PostTypeId=\"2\"") != std::string::npos) {
//            auto loc = s.find("ParentId=\"") + 10;
//            auto id = std::stoul(s.substr(loc, s.find('\"', loc) - loc));
//            if (std::find(ids.begin(), ids.end(), id) != ids.end() || (contains(id, s) == 1)) {
//                get_stream(id) << s << '\n';
//            }
//        }
//        a++;
//        if (reset) {
//            std::cout << " Buffer reset at " << a << " entries" << std::endl;
//            reset = false;
//        }
//    }

    auto iterator = std::filesystem::directory_iterator("./data/");
    auto count = 0ul;
    for (const auto& file : iterator){
        std::ifstream ifile(file.path());
        std::filesystem::path temp("./processed");
        temp /= file.path().filename();
        std::ofstream ofile(temp);

        std::string line;
        auto i = 0;
        while(std::getline(ifile, line)){
            auto body_loc = line.find("Body=\"") + 6;
            auto body = line.substr(body_loc, line.find("\"", body_loc) - body_loc);

            if (i == 0){
                // make question tag
                auto title_loc = line.find("Title=\"") + 7;
                auto title = line.substr(title_loc, line.find("\"", title_loc) - title_loc);

                auto tags_loc = line.find("Tags=\"") + 6;
                auto tags = line.substr(tags_loc, line.find("\"", tags_loc) - tags_loc);
                ofile << "<file>\n<question>\n" << "<Title>" << title << "</Title>\n" << "<Body>" << body << "</Body>\n" << "<Tags>" << tags << "</Tags>\n</question>\n";
                i = 1;
            }
            else{
                ofile << "<answer>\n<Body>" << body << "</Body>\n</answer>\n";
            }
        }
        ofile << "</file>";
        ofile.close();
        count ++;
        if ((count % 1000) == 1){
            std::cout << count << " / 2.000.000 (" << float(100*count)/2000000 << "%\n";
        }
    }

}